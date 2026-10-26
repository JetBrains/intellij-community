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
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A regression test for IJPL-259465.
 *
 * The model lists the children of a directory on one shared background thread.
 * A custom NIO [FileSystemProvider] makes [FileSystemProvider.readAttributes] of one child block on a latch.
 * This models a symlink that points to a mount that does not answer.
 *
 * The model must stay responsive:
 * the parent listing must complete, the stuck child must appear as unresolved,
 * and a later Refresh must still run on the shared thread.
 */
@TestApplication
class NioFileTreeModelBlockingChildTest {
  @TempDir
  lateinit var root: Path

  private fun descriptor(): FileChooserDescriptor = FileChooserDescriptor(true, true, false, false)

  private fun contributor(): UniversalFileChooserContributor = object : UniversalFileChooserContributor {
    override val tabTitle: String = "Test"
    override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = emptyList()
    override fun ownsPath(path: Path): Boolean = true
  }

  private fun <T> NioFileTreeModel.onInvoker(timeoutSeconds: Long, task: () -> T): T =
    invoker.compute(task).blockingGet(timeoutSeconds.toInt(), TimeUnit.SECONDS)!!

  @Test
  fun `a child that blocks on readAttributes does not freeze the listing or Refresh`() {
    // One child blocks its readAttributes until the test releases the latch.
    val latch = CountDownLatch(1)
    val blockedName = "blocked"

    Files.createDirectory(root.resolve("alpha"))
    Files.createDirectory(root.resolve(blockedName))
    Files.createDirectory(root.resolve("gamma"))

    val provider = BlockingReadAttributesFsProvider(blockedName, latch)
    val fsRoot = provider.wrap(root)
    val descriptor = descriptor().withPathRoots(fsRoot)
    val model = NioFileTreeModel(descriptor, contributor = contributor())
    try {
      // The parent listing must complete even though one child blocks on readAttributes.
      val count = model.onInvoker(20) { model.getChildCount(model.root) }
      assertThat(count).isEqualTo(3)

      val children = model.onInvoker(20) {
        (0 until model.getChildCount(model.root)).map { model.getChild(model.root, it) as NioFileNode }
      }
      val byName = children.associateBy { it.path?.fileName?.toString() }
      assertThat(byName.keys).containsExactlyInAnyOrder("alpha", blockedName, "gamma")

      // The stuck child appears, but it stays unresolved.
      assertThat(byName.getValue(blockedName).isValid).isFalse()
      // The other children resolve as usual.
      assertThat(byName.getValue("alpha").isValid).isTrue()
      assertThat(byName.getValue("gamma").isValid).isTrue()

      // Refresh must still run on the shared thread, so a new task completes without the latch.
      model.invalidate()
      val ping = model.onInvoker(20) { "ok" }
      assertThat(ping).isEqualTo("ok")
    }
    finally {
      latch.countDown()
      Disposer.dispose(model)
    }
  }
}

/**
 * A file system provider that delegates every operation to the default file system.
 * It only blocks [readAttributes] of the child named [blockedName] until [latch] opens.
 */
private class BlockingReadAttributesFsProvider(
  private val blockedName: String,
  private val latch: CountDownLatch,
) : FileSystemProvider() {

  private val delegate: FileSystemProvider = java.nio.file.FileSystems.getDefault().provider()
  private val fileSystem = BlockingReadAttributesFileSystem(this)

  fun wrap(path: Path): Path = BlockingPath(fileSystem, path)

  private fun unwrap(path: Path?): Path? = when (path) {
    null -> null
    is BlockingPath -> path.delegate
    else -> path
  }

  override fun getScheme(): String = "blocking-read-attributes"

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
    if (path.fileName?.toString() == blockedName) {
      latch.await()
    }
    return delegate.readAttributes(unwrap(path), type, *options)
  }

  override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): MutableMap<String, Any> {
    if (path.fileName?.toString() == blockedName) {
      latch.await()
    }
    return delegate.readAttributes(unwrap(path), attributes, *options)
  }

  override fun setAttribute(path: Path, attribute: String, value: Any?, vararg options: LinkOption) =
    delegate.setAttribute(unwrap(path), attribute, value, *options)

  override fun readSymbolicLink(link: Path): Path = wrap(delegate.readSymbolicLink(unwrap(link)))
}

private class BlockingReadAttributesFileSystem(private val provider: BlockingReadAttributesFsProvider) : FileSystem() {
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
private class BlockingPath(
  private val fileSystem: BlockingReadAttributesFileSystem,
  val delegate: Path,
) : Path {
  private fun wrap(path: Path?): Path? = if (path == null) null else BlockingPath(fileSystem, path)

  private fun unwrap(other: Path): Path = if (other is BlockingPath) other.delegate else other

  override fun getFileSystem(): FileSystem = fileSystem

  override fun isAbsolute(): Boolean = delegate.isAbsolute

  override fun getRoot(): Path? = wrap(delegate.root)

  override fun getFileName(): Path? = wrap(delegate.fileName)

  override fun getParent(): Path? = wrap(delegate.parent)

  override fun getNameCount(): Int = delegate.nameCount

  override fun getName(index: Int): Path = wrap(delegate.getName(index))!!

  override fun subpath(beginIndex: Int, endIndex: Int): Path = wrap(delegate.subpath(beginIndex, endIndex))!!

  override fun startsWith(other: Path): Boolean = other is BlockingPath && delegate.startsWith(unwrap(other))

  override fun endsWith(other: Path): Boolean = other is BlockingPath && delegate.endsWith(unwrap(other))

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
    other is BlockingPath && delegate == other.delegate && fileSystem === other.fileSystem

  override fun hashCode(): Int = delegate.hashCode()
}
