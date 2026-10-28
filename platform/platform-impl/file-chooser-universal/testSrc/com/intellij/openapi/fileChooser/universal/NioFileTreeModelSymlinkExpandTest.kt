// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A test for the expandable state of a symlink node (IJPL-259465).
 *
 * A custom NIO [FileSystemProvider] presents some children as symlinks:
 * a NOFOLLOW attribute read returns fake symlink attributes,
 * and a follow read blocks on a latch and then returns the real attributes.
 *
 * A symlink node must not be expandable while its target is not resolved.
 * The node becomes expandable only when the resolution finds a directory target.
 */
@TestApplication
class NioFileTreeModelSymlinkExpandTest {
  @TempDir
  lateinit var root: Path

  private fun descriptor(): FileChooserDescriptor = FileChooserDescriptor(true, true, false, false)

  private fun contributor(): UniversalFileChooserContributor = object : UniversalFileChooserContributor {
    override val tabTitle: String = "Test"
    override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = emptyList()
    override fun ownsPath(path: Path): Boolean = true
  }

  private fun <T> NioFileTreeModel.onInvoker(task: () -> T): T =
    invoker.compute(task).blockingGet(20, TimeUnit.SECONDS)!!

  private fun NioFileTreeModel.waitOnInvoker(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    while (!onInvoker(condition)) {
      assertThat(System.nanoTime()).describedAs("the condition did not come true in time").isLessThan(deadline)
      Thread.sleep(10)
    }
  }

  @Test
  fun `a symlink is not expandable until the resolution finds a directory target`() {
    // The follow read of each link blocks until the test releases the latch.
    val latch = CountDownLatch(1)
    val dirLink = "dir-link"
    val fileLink = "file-link.txt"

    Files.createDirectory(root.resolve(dirLink))
    Files.createFile(root.resolve(dirLink).resolve("inside.txt"))
    Files.createFile(root.resolve(fileLink))

    val provider = FakeSymlinkFsProvider(setOf(dirLink, fileLink), latch)
    val fsRoot = provider.wrap(root)
    val descriptor = descriptor().withPathRoots(fsRoot)
    val model = NioFileTreeModel(descriptor, contributor = contributor())
    try {
      val children = model.onInvoker {
        (0 until model.getChildCount(model.root)).map { model.getChild(model.root, it) as NioFileNode }
      }
      val byName = children.associateBy { it.path?.fileName?.toString() }
      assertThat(byName.keys).containsExactlyInAnyOrder(dirLink, fileLink)

      // The targets are not resolved yet: both symlinks stay non-expandable.
      assertThat(model.onInvoker { model.isLeaf(byName.getValue(dirLink)) }).isTrue()
      assertThat(model.onInvoker { model.isLeaf(byName.getValue(fileLink)) }).isTrue()

      val fileNode = byName.getValue(fileLink)
      val unresolvedIcon = fileNode.icon
      latch.countDown()

      // The resolution finds a directory target: the node becomes expandable.
      model.waitOnInvoker { !model.isLeaf(byName.getValue(dirLink)) }
      val count = model.onInvoker { model.getChildCount(byName.getValue(dirLink)) }
      assertThat(count).isEqualTo(1)

      // The resolution finds a file target: the icon changes, but the node stays non-expandable.
      model.waitOnInvoker { fileNode.icon !== unresolvedIcon }
      assertThat(model.onInvoker { model.isLeaf(fileNode) }).isTrue()
    }
    finally {
      latch.countDown()
      Disposer.dispose(model)
    }
  }
}

/**
 * A file system provider that delegates every operation to the default file system.
 * It presents each child named in [linkNames] as a symlink:
 * a NOFOLLOW attribute read returns [FakeSymlinkAttributes],
 * and a follow read blocks on [latch] and then returns the real attributes.
 */
private class FakeSymlinkFsProvider(
  private val linkNames: Set<String>,
  private val latch: CountDownLatch,
) : FileSystemProvider() {

  private val delegate: FileSystemProvider = java.nio.file.FileSystems.getDefault().provider()
  private val fileSystem = FakeSymlinkFileSystem(this)

  fun wrap(path: Path): Path = FakeSymlinkPath(fileSystem, path)

  private fun unwrap(path: Path?): Path? = when (path) {
    null -> null
    is FakeSymlinkPath -> path.delegate
    else -> path
  }

  override fun getScheme(): String = "fake-symlink"

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): FileSystem = fileSystem

  override fun getFileSystem(uri: URI): FileSystem = fileSystem

  override fun getPath(uri: URI): Path = wrap(delegate.getPath(uri))

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel? =
    delegate.newByteChannel(unwrap(path), options, *attrs)

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> {
    val delegateStream = delegate.newDirectoryStream(unwrap(dir), filter)
    return object : DirectoryStream<Path> {
      override fun iterator(): MutableIterator<Path> {
        val delegateIterator = delegateStream.iterator()
        return object : MutableIterator<Path> {
          override fun hasNext(): Boolean = delegateIterator.hasNext()
          override fun next(): Path = wrap(delegateIterator.next())
          override fun remove() = delegateIterator.remove()
        }
      }

      override fun close() = delegateStream.close()
    }
  }

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) = delegate.createDirectory(unwrap(dir), *attrs)

  override fun delete(path: Path) = delegate.delete(unwrap(path))

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) = delegate.copy(unwrap(source), unwrap(target), *options)

  override fun move(source: Path, target: Path, vararg options: CopyOption?) = delegate.move(unwrap(source), unwrap(target), *options)

  override fun isSameFile(path: Path, path2: Path): Boolean = delegate.isSameFile(unwrap(path), unwrap(path2))

  override fun isHidden(path: Path): Boolean = delegate.isHidden(unwrap(path))

  override fun getFileStore(path: Path): FileStore = delegate.getFileStore(unwrap(path))

  override fun checkAccess(path: Path, vararg modes: AccessMode?) = delegate.checkAccess(unwrap(path), *modes)

  override fun <V : FileAttributeView> getFileAttributeView(path: Path, type: Class<V>, vararg options: LinkOption): V =
    delegate.getFileAttributeView(unwrap(path), type, *options)

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    if (path.fileName?.toString() !in linkNames) {
      return delegate.readAttributes(unwrap(path), type, *options)
    }
    if (LinkOption.NOFOLLOW_LINKS in options) {
      val real = delegate.readAttributes(unwrap(path), BasicFileAttributes::class.java, *options)
      @Suppress("UNCHECKED_CAST")
      return FakeSymlinkAttributes(real) as A
    }
    latch.await()
    return delegate.readAttributes(unwrap(path), type, *options)
  }

  override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): MutableMap<String, Any> =
    delegate.readAttributes(unwrap(path), attributes, *options)

  override fun setAttribute(path: Path, attribute: String, value: Any?, vararg options: LinkOption) =
    delegate.setAttribute(unwrap(path), attribute, value, *options)

  override fun readSymbolicLink(link: Path): Path = wrap(delegate.readSymbolicLink(unwrap(link)))
}

/** The NOFOLLOW attributes of a fake symlink: the entry reports itself as a symlink and hides the real kind. */
private class FakeSymlinkAttributes(private val delegate: BasicFileAttributes) : BasicFileAttributes {
  override fun lastModifiedTime(): FileTime = delegate.lastModifiedTime()
  override fun lastAccessTime(): FileTime = delegate.lastAccessTime()
  override fun creationTime(): FileTime = delegate.creationTime()
  override fun isRegularFile(): Boolean = false
  override fun isDirectory(): Boolean = false
  override fun isSymbolicLink(): Boolean = true
  override fun isOther(): Boolean = false
  override fun size(): Long = delegate.size()
  override fun fileKey(): Any? = delegate.fileKey()
}

private class FakeSymlinkFileSystem(private val provider: FakeSymlinkFsProvider) : FileSystem() {
  private val delegate: FileSystem = java.nio.file.FileSystems.getDefault()

  override fun provider(): FileSystemProvider = provider

  override fun close() = delegate.close()

  override fun isOpen(): Boolean = delegate.isOpen

  override fun isReadOnly(): Boolean = delegate.isReadOnly

  override fun getSeparator(): String = delegate.separator

  override fun getRootDirectories(): Iterable<Path> = delegate.rootDirectories.map { provider.wrap(it) }

  override fun getFileStores(): Iterable<FileStore> = delegate.fileStores

  override fun supportedFileAttributeViews(): Set<String> = delegate.supportedFileAttributeViews()

  override fun getPath(first: String, vararg more: String): Path = provider.wrap(delegate.getPath(first, *more))

  override fun getPathMatcher(syntaxAndPattern: String): PathMatcher = delegate.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService = delegate.userPrincipalLookupService

  override fun newWatchService(): WatchService = delegate.newWatchService()
}

/**
 * A path whose [getFileSystem] returns the custom file system, so operations route through its provider.
 * Every other operation delegates to the real [delegate] path.
 */
private class FakeSymlinkPath(
  private val fileSystem: FakeSymlinkFileSystem,
  val delegate: Path,
) : Path {
  private fun wrap(path: Path?): Path? = if (path == null) null else FakeSymlinkPath(fileSystem, path)

  private fun unwrap(other: Path): Path = if (other is FakeSymlinkPath) other.delegate else other

  override fun getFileSystem(): FileSystem = fileSystem

  override fun isAbsolute(): Boolean = delegate.isAbsolute

  override fun getRoot(): Path? = wrap(delegate.root)

  override fun getFileName(): Path? = wrap(delegate.fileName)

  override fun getParent(): Path? = wrap(delegate.parent)

  override fun getNameCount(): Int = delegate.nameCount

  override fun getName(index: Int): Path = wrap(delegate.getName(index))!!

  override fun subpath(beginIndex: Int, endIndex: Int): Path = wrap(delegate.subpath(beginIndex, endIndex))!!

  override fun startsWith(other: Path): Boolean = other is FakeSymlinkPath && delegate.startsWith(unwrap(other))

  override fun endsWith(other: Path): Boolean = other is FakeSymlinkPath && delegate.endsWith(unwrap(other))

  override fun normalize(): Path = wrap(delegate.normalize())!!

  override fun resolve(other: Path): Path = wrap(delegate.resolve(unwrap(other)))!!

  override fun relativize(other: Path): Path = wrap(delegate.relativize(unwrap(other)))!!

  override fun toUri(): URI = delegate.toUri()

  override fun toAbsolutePath(): Path = wrap(delegate.toAbsolutePath())!!

  override fun toRealPath(vararg options: LinkOption): Path = wrap(delegate.toRealPath(*options))!!

  @Suppress("IO_FILE_USAGE")
  override fun toFile(): File = delegate.toFile()

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey =
    delegate.register(watcher, events, *modifiers)

  override fun iterator(): MutableIterator<Path> {
    val delegateIterator = delegate.iterator()
    return object : MutableIterator<Path> {
      override fun hasNext(): Boolean = delegateIterator.hasNext()
      override fun next(): Path = wrap(delegateIterator.next())!!
      override fun remove() = delegateIterator.remove()
    }
  }

  override fun compareTo(other: Path): Int = delegate.compareTo(unwrap(other))

  override fun toString(): String = delegate.toString()

  override fun equals(other: Any?): Boolean =
    other is FakeSymlinkPath && delegate == other.delegate && fileSystem === other.fileSystem

  override fun hashCode(): Int = delegate.hashCode()
}
