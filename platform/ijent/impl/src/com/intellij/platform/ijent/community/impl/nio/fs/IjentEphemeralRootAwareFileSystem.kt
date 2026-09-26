// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.fs

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2
import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFsPath
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.EelDescriptorOwner
import com.intellij.platform.eel.provider.utils.EelPathTransfer
import com.intellij.platform.eel.provider.utils.WindowsPathUtils
import com.intellij.platform.eel.provider.utils.impl.ijentToLocal
import com.intellij.platform.eel.provider.utils.impl.localToIjent
import com.intellij.platform.ijent.community.impl.nio.AbsoluteIjentNioPath
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.CopyOption
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.ProviderMismatchException
import java.nio.file.StandardCopyOption
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

private fun Path.unwrap(): Path = if (this is MultiRoutingFsPath) currentDelegate else this

private fun Path.toIjentPathOrNull(): IjentNioPath? = when (val path = unwrap()) {
  is IjentEphemeralRootAwarePath -> path.originalPath
  is IjentNioPath -> path
  else -> null
}

private fun Path.toIjentPath(): IjentNioPath = toIjentPathOrNull() ?: throw IllegalArgumentException("Cannot convert $this to IjentNioPath")

private fun Path.toOriginalPath(): Path = toIjentPathOrNull() ?: this

/**
 * The `RootAwarePath `class delegates all operations to the original IjentNioPath.
 * The root is used only as an information holder and for computing the `toUri` and `toString`.
 */
@Suppress("NAME_SHADOWING")
@ApiStatus.Internal
class IjentEphemeralRootAwarePath(
  private val fileSystem: IjentEphemeralRootAwareFileSystem,
  val rootPath: Path,
  val originalPath: IjentNioPath,
) : Path, BasicFileAttributesHolder2.Impl(originalPath.getCachedFileAttributesAndWrapToDosAttributesAdapterIfNeeded()) {
  override fun getFileSystem(): FileSystem = fileSystem

  private class ActualPathInfo(val actualPath: Path, val diverges: Boolean)

  // Lazy: computing the actual path renders and parses the whole path through the original file system, which is
  // too expensive to do for every constructed path object (directory streams wrap every entry), and the parse can
  // fail for environment file names that the original file system cannot represent (e.g. `*` on Windows).
  private val actualPathInfo: ActualPathInfo by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val actualPathOnOriginalFs = fileSystem.actualPathOnOriginalFs
    if (actualPathOnOriginalFs == null || !originalPath.isAbsolute) {
      return@lazy ActualPathInfo(originalPath, false)
    }
    val presentable =
      try {
        fileSystem.originalFs.getPath(toString())
      }
      catch (_: InvalidPathException) {
        // The name is not representable on the original file system; IJent-side operations keep working.
        null
      }
    if (presentable == null) {
      ActualPathInfo(originalPath, false)
    }
    else {
      val actual = actualPathOnOriginalFs(presentable)
      ActualPathInfo(actual, actual != presentable)
    }
  }

  /**
   * The path that physically backs this path on the original file system, when that file system sees the environment
   * files: the presentable rendering mapped through [IjentEphemeralRootAwareFileSystem.actualPathOnOriginalFs].
   * Otherwise it is the original IJent path itself.
   */
  val actualPath: Path get() = actualPathInfo.actualPath

  /** `true` when [actualPath] is a different location than the presentable rendering (a mount of a host location). */
  internal val actualPathDiverges: Boolean get() = actualPathInfo.diverges

  override fun invalidate() {
    originalPath.invalidate()
    super.invalidate()
  }

  override fun isAbsolute(): Boolean {
    return originalPath.isAbsolute
  }

  override fun getRoot(): Path? {
    return originalPath.root?.let { IjentEphemeralRootAwarePath(fileSystem, rootPath, it) }
  }

  override fun getFileName(): Path? {
    // Only the environment root has no file name; an empty relative path keeps the delegate behavior.
    if (originalPath.isAbsolute && originalPath.nameCount == 0) return null
    val fileName = originalPath.fileName ?: return null
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, fileName as IjentNioPath)
  }

  override fun getParent(): Path? {
    val parent = originalPath.parent ?: return null
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, parent)
  }

  override fun getNameCount(): Int {
    return originalPath.nameCount
  }

  override fun getName(index: Int): Path {
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.getName(index) as IjentNioPath)
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path {
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.subpath(beginIndex, endIndex))
  }

  override fun startsWith(other: Path): Boolean {
    val other = other.unwrap()
    return originalPath.startsWith(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun endsWith(other: Path): Boolean {
    val other = other.unwrap()
    return originalPath.endsWith(if (other is IjentEphemeralRootAwarePath) other.originalPath else other)
  }

  override fun normalize(): Path {
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.normalize())
  }

  override fun resolve(other: Path): Path {
    val other = other.unwrap()
    if (other.isAbsolute) return other
    return IjentEphemeralRootAwarePath(fileSystem,
                                       rootPath,
                                       originalPath.resolve(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun relativize(other: Path): Path {
    val other = other.unwrap()
    if (isAbsolute != other.isAbsolute) {
      throw IllegalArgumentException(
        "Tried to relativize a relative and an absolute path: `$this` and `$other`." +
        " Check for possible confusion." +
        " Maybe some code up the call stack tried to use a path from the remote environment as a local path."
      )
    }
    return IjentEphemeralRootAwarePath(fileSystem,
                                       rootPath,
                                       originalPath.relativize(if (other is IjentEphemeralRootAwarePath) other.originalPath else other))
  }

  override fun toUri(): URI {
    if (isAbsolute && fileSystem.eelDescriptor.osFamily == EelOsFamily.Windows) {
      return fileSystem.originalFs.getPath(toString()).toUri()
    }
    return rootPath.resolve(ijentToLocal(originalPath.pathString.removePrefix("/"))).toUri()
  }

  override fun toAbsolutePath(): Path {
    return IjentEphemeralRootAwarePath(fileSystem, rootPath, originalPath.toAbsolutePath())
  }

  override fun toRealPath(vararg options: LinkOption): Path {
    if (!isAbsolute) {
      return toAbsolutePath().toRealPath(*options)
    }

    if (normalize().toString() == rootPath.toString()) {
      return this
    }

    val ijentNioRealPath = if (actualPathDiverges) {
      // The rendering is a mount of a location outside the environment: resolving links there from inside the environment
      // fails with permission errors, so the path is only normalized.
      originalPath.normalize()
    }
    else {
      originalPath.toRealPath(*options)
    }

    return IjentEphemeralRootAwarePath(fileSystem, rootPath, ijentNioRealPath)
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier?): WatchKey =
    originalPath.register(watcher, events, *modifiers.filterNotNull().toTypedArray())

  override fun compareTo(other: Path): Int {
    val other = other.unwrap()
    if (other !is IjentEphemeralRootAwarePath) {
      return originalPath.compareTo(other)
    }
    val byOriginalPath = originalPath.compareTo(other.originalPath)
    if (byOriginalPath != 0) {
      return byOriginalPath
    }
    if (fileSystem == other.fileSystem) {
      return 0
    }
    // The same environment path in two different environments: order deterministically instead of colliding.
    return fileSystem.eelDescriptor.toString().compareTo(other.fileSystem.eelDescriptor.toString())
  }

  override fun toFile(): File {
    return originalPath.toFile()
  }

  // The original IJent path is already free of the root notation: paths reached through different notations of one
  // root (`\\wsl$` vs `\\wsl.localhost`) carry equal original paths, while different environment files never do.
  // Together with the value-based file system equality this defines one identity per environment file.
  // `hashCode` delegates to the same components; `compareTo` too, within one environment.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Path) return false

    val other = other.unwrap()

    if (other !is IjentEphemeralRootAwarePath) {
      return false
    }

    return fileSystem == other.fileSystem && originalPath == other.originalPath
  }

  override fun hashCode(): Int {
    return fileSystem.hashCode() * 31 + originalPath.hashCode()
  }

  private val asString by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (isAbsolute) {
      when (fileSystem.eelDescriptor.osFamily) {
        EelOsFamily.Posix -> {
          // Rendered by plain string concatenation: parsing through the original file system could fail
          // for names it cannot represent (e.g. `*` on Windows), and toString must never throw.
          val relative = ijentToLocal(originalPath.pathString.removePrefix("/")).replace("/", fileSystem.separator)
          if (relative.isEmpty()) rootPath.pathString
          else rootPath.pathString.removeSuffix(fileSystem.separator) + fileSystem.separator + relative
        }
        EelOsFamily.Windows -> {
          WindowsPathUtils.getNioPathString(rootPath.toString(), (originalPath as AbsoluteIjentNioPath).eelPath, fileSystem.separator)
        }
      }
    }
    else {
      // A relative path is rendered with the separator of this file system, like an absolute one.
      ijentToLocal(originalPath.toString()).replace(originalPath.fileSystem.separator, fileSystem.separator)
    }
  }

  override fun toString(): String {
    return asString
  }
}

@ApiStatus.Internal
class IjentEphemeralRootAwareFileSystemProvider(
  val root: Path,
  private val ijentFsProvider: FileSystemProvider,
  private val originalFsProvider: FileSystemProvider,
  private val useRootDirectoriesFromOriginalFs: Boolean,
  override val eelDescriptor: EelDescriptor,
  /**
   * Whether [root] must match path strings case-insensitively.
   * A root that is a Windows-side string (a UNC root) requires `true`; a posix root requires `false`.
   */
  private val caseInsensitiveRoot: Boolean = false,
  /**
   * Maps the presentable rendering of an environment path (a path of the original file system under [root]) to the
   * path that physically backs it on the original file system. Set it only when the original file system sees the
   * files of the environment; then it also serves as the fallback for operations that IJent does not support.
   * `null` when the original file system cannot see the environment files (Docker, SSH, TCP).
   */
  private val actualPathOnOriginalFs: ((presentablePath: Path) -> Path)? = null,
) : DelegatingFileSystemProvider<IjentEphemeralRootAwareFileSystemProvider, IjentEphemeralRootAwareFileSystem>(),
    RoutingAwareFileSystemProvider, EelDescriptorOwner {
  private val originalFs = originalFsProvider.getFileSystem(URI("file:/"))

  private val wrappedFileSystems = ConcurrentHashMap<FileSystem, IjentEphemeralRootAwareFileSystem>()

  override fun wrapDelegateFileSystem(delegateFs: FileSystem): IjentEphemeralRootAwareFileSystem {
    return wrappedFileSystems.computeIfAbsent(delegateFs) {
      IjentEphemeralRootAwareFileSystem(rootAwareFileSystemProvider = this,
                                        ijentFs = delegateFs,
                                        originalFs = originalFs,
                                        useRootDirectoriesFromOriginalFs = useRootDirectoriesFromOriginalFs,
                                        eelDescriptor = eelDescriptor,
                                        caseInsensitiveRoot = caseInsensitiveRoot,
                                        actualPathOnOriginalFs = actualPathOnOriginalFs)
    }
  }

  override fun getScheme(): String? {
    return originalFsProvider.scheme
  }

  override fun <V : FileAttributeView> getFileAttributeView(path: Path, type: Class<V>, vararg options: LinkOption): V {
    return when {
      originalFs.supportedFileAttributeViews().contains("dos") && eelDescriptor.osFamily == EelOsFamily.Posix -> {
        ijentFsProvider.getFileAttributeViewUsingDosAttributesAdapter(path.toIjentPath(), type, *options)
      }
      else -> super.getFileAttributeView(path, type, *options)
    }
  }

  override fun newAsynchronousFileChannel(
    path: Path,
    options: MutableSet<out OpenOption>,
    executor: ExecutorService?,
    vararg attrs: FileAttribute<*>?,
  ): AsynchronousFileChannel {
    if (actualPathOnOriginalFs != null && path.isAbsolute) {
      return originalFsProvider.newAsynchronousFileChannel(originalFs.getPath(path.toString()), options, executor, *attrs)
    }
    throw UnsupportedOperationException("Asynchronous file channels are not supported for $eelDescriptor")
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    return when {
      originalFs.supportedFileAttributeViews().contains("dos") && eelDescriptor.osFamily == EelOsFamily.Posix -> {
        ijentFsProvider.readAttributesUsingDosAttributesAdapter(path, path.toIjentPath(), type, *options)
      }
      else -> super.readAttributes(path, type, *options)
    }
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    // Equal file systems wrap one IJent file system, whatever the notation of their roots, so IJent can do the work natively.
    if (source.fileSystem == target.fileSystem) {
      super.copy(source, target, *options)
    }
    else if (source.toIjentPathOrNull() == null && target.toIjentPathOrNull() == null) {
      LOG.warn("This branch is not supposed to execute. Copying $source => $target through an inappropriate FileSystemProvider")
      originalFsProvider.copy(source.unwrap(), target.unwrap(), *options)
    }
    else {
      EelPathTransfer.walkingTransfer(source.toOriginalPath(),
                                      target.toOriginalPath(),
                                      removeSource = false,
                                      copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in options)
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    // Equal file systems wrap one IJent file system, whatever the notation of their roots, so IJent can do the work natively.
    if (source.fileSystem == target.fileSystem) {
      super.move(source, target, *options)
    }
    else if (source.toIjentPathOrNull() == null && target.toIjentPathOrNull() == null) {
      LOG.warn("This branch is not supposed to execute. Moving $source => $target through an inappropriate FileSystemProvider")
      originalFsProvider.move(source.unwrap(), target.unwrap(), *options)
    }
    else {
      EelPathTransfer.walkingTransfer(source.toOriginalPath(),
                                      target.toOriginalPath(),
                                      removeSource = true,
                                      copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in options)
    }
  }

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider {
    return ijentFsProvider
  }

  public override fun wrapDelegatePath(delegatePath: Path?): Path? {
    if (delegatePath == null) return null

    if (delegatePath is IjentNioPath) {
      return IjentEphemeralRootAwarePath(wrapDelegateFileSystem(delegatePath.fileSystem), root, delegatePath)
    }

    return delegatePath
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    if (path !is IjentEphemeralRootAwarePath) {
      if (path2 !is IjentEphemeralRootAwarePath) {
        throw ProviderMismatchException("Neither $path (${path::class}) nor $path2 (${path2::class}) are ${IjentEphemeralRootAwarePath::class.java.name}")
      }
      return isSameFile(path2, path)
    }

    if (path2 !is IjentEphemeralRootAwarePath) {
      return path.actualPath.fileSystem.provider() == path2.fileSystem.provider() && Files.isSameFile(path.actualPath, path2)
    }

    if (!path.actualPathDiverges && !path2.actualPathDiverges) {
      return Files.isSameFile(path.toIjentPath(), path2.toIjentPath())
    }

    if (path.actualPath.fileSystem.provider() == path2.actualPath.fileSystem.provider()) {
      return Files.isSameFile(path.actualPath, path2.actualPath)
    }

    return false
  }

  override fun toDelegatePath(path: Path?): Path? {
    if (path is IjentEphemeralRootAwarePath) {
      // The path may come from an equal file system created for another root of the same environment
      // (e.g. `\\wsl$` vs `\\wsl.localhost`), so the roots are not required to match.
      return path.originalPath
    }

    return path
  }

  override fun canHandleRouting(path: Path): Boolean {
    return true
  }

  private companion object {
    private val LOG = logger<IjentEphemeralRootAwareFileSystemProvider>()
  }
}

/**
 * - getPath: returns a `RootAwarePath` when a prefix is present.
 * - getRootDirectories: returns *only* the `root`.
 */
@ApiStatus.Internal
class IjentEphemeralRootAwareFileSystem(
  private val rootAwareFileSystemProvider: IjentEphemeralRootAwareFileSystemProvider,
  private val ijentFs: FileSystem,
  internal val originalFs: FileSystem,
  private val useRootDirectoriesFromOriginalFs: Boolean,
  override val eelDescriptor: EelDescriptor,
  private val caseInsensitiveRoot: Boolean = false,
  internal val actualPathOnOriginalFs: ((presentablePath: Path) -> Path)? = null,
) : DelegatingFileSystem<IjentEphemeralRootAwareFileSystemProvider>(), EelDescriptorOwner {
  private val root: Path = rootAwareFileSystemProvider.root
  private val invariantSeparatorRootPathString = root.invariantSeparatorsPathString.removeSuffix("/")

  override fun getDelegate(): FileSystem {
    return ijentFs
  }

  override fun getRootDirectories(): Iterable<Path?> {
    return if (useRootDirectoriesFromOriginalFs) {
      originalFs.rootDirectories
    }
    else {
      ijentFs.rootDirectories.map { rootAwareFileSystemProvider.wrapDelegatePath(it) }
    }
  }

  override fun close() {
    ijentFs.close()
  }

  override fun getPath(first: String, vararg more: String): Path {
    if (isPathUnderRoot(first)) {
      val parts = more.flatMap { it.split(root.fileSystem.separator) }.filter(String::isNotEmpty).toTypedArray()
      val relativized = relativizeToRoot(first, parts, eelDescriptor)
      val ijentNioPath =
        ijentFs.getPath(localToIjent(relativized.first()), *relativized.drop(1).map { localToIjent(it) }.toTypedArray()) as IjentNioPath
      return IjentEphemeralRootAwarePath(this, root, ijentNioPath)
    }

    // A path string outside the root is relative or foreign and arrives in the presentable rendering of this file system,
    // so its separators and special characters are mapped back the same way as for a path under the root.
    val delegateFs = getDelegate(first)
    val first = localToIjent(first.replace(originalFs.separator, delegateFs.separator))
    val more = more.toList().map { localToIjent(it.replace(originalFs.separator, delegateFs.separator)) }.toTypedArray()
    return super.getPath(first, *more)
  }

  override fun provider(): IjentEphemeralRootAwareFileSystemProvider {
    return rootAwareFileSystemProvider
  }

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher = originalFs.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService = originalFs.userPrincipalLookupService

  override fun newWatchService(): WatchService = ijentFs.newWatchService()

  override fun getFileStores(): Iterable<FileStore> = originalFs.fileStores + ijentFs.fileStores

  override fun isOpen(): Boolean = true

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String = originalFs.separator

  override fun supportedFileAttributeViews(): Set<String> = buildSet {
    addAll(originalFs.supportedFileAttributeViews())
    addAll(ijentFs.supportedFileAttributeViews())
  }

  // Neither the root nor the descriptor is a part of equality: the file systems created for different notations of one
  // environment root (`\\wsl$` vs `\\wsl.localhost`) carry different descriptors but wrap one IJent file system, and they
  // must be equal, and so must the paths they produce. `ijentFs` identifies the environment; `actualPathOnOriginalFs`
  // is a property of the environment kind.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IjentEphemeralRootAwareFileSystem) return false
    return ijentFs == other.ijentFs &&
           originalFs == other.originalFs &&
           useRootDirectoriesFromOriginalFs == other.useRootDirectoriesFromOriginalFs &&
           caseInsensitiveRoot == other.caseInsensitiveRoot
  }

  private fun isPathUnderRoot(path: String): Boolean {
    val invariantPath = toSystemIndependentName(path)
    if (!invariantPath.startsWith(invariantSeparatorRootPathString, ignoreCase = caseInsensitiveRoot)) {
      return false
    }
    // A path sharing the root as a plain string prefix (`/mnt/root-sibling` for the root `/mnt/root`) is not under the root.
    return invariantPath.length == invariantSeparatorRootPathString.length || invariantPath[invariantSeparatorRootPathString.length] == '/'
  }

  private fun relativizeToRoot(path: String, parts: Array<String>, eelDescriptor: EelDescriptor): Array<String> {
    // `isPathUnderRoot` has verified the prefix; `substring` also works when the prefix matched case-insensitively.
    val relativePath = toSystemIndependentName(path).substring(invariantSeparatorRootPathString.length).nullize()
    return when (eelDescriptor.osFamily) {
      EelOsFamily.Posix -> {
        arrayOf(relativePath ?: "/", *parts)
      }
      EelOsFamily.Windows -> {
        if (relativePath != null) {
          arrayOf(WindowsPathUtils.rootRelativeToEelPath(relativePath.removePrefix("/")), *parts)
        }
        else if (parts.isNotEmpty()) {
          parts
        }
        else {
          throw IllegalArgumentException(
            "The mount root `$root` of a Windows environment cannot be represented as a single path: it fans out to per-drive roots")
        }
      }
    }
  }

  override fun hashCode(): Int {
    var result = useRootDirectoriesFromOriginalFs.hashCode()
    result = 31 * result + ijentFs.hashCode()
    result = 31 * result + originalFs.hashCode()
    result = 31 * result + caseInsensitiveRoot.hashCode()
    return result
  }
}
