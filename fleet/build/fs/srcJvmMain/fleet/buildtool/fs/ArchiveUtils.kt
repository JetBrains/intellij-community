package fleet.buildtool.fs

import fleet.buildtool.fs.FilePermissions.Posix
import fleet.buildtool.fs.ReproducibilityMode.Reproducible.PermissionOption
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants.LF_SYMLINK
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.slf4j.Logger
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.fileAttributesView
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Like [org.gradle.kotlin.dsl.support.zipTo] but reproducible
 */
fun zip(targetZipFile: Path, directory: Path, withTopLevelFolder: Boolean) {
  val filesToZip = directory.toFile()
    .walkTopDown()
    .filter { it.isFile }
    .map { file ->
      val relativePath = when (withTopLevelFolder) {
        true -> file.toPath().relativeTo(directory.parent)
        false -> file.toPath().relativeTo(directory)
      }.invariantSeparatorsPathString // reproducible ZIP entry names regardless of the platform
      relativePath to file.inputStream()
    }
  zip(targetZipFile, filesToZip)
}

fun zip(targetZipFile: Path, filesToZip: Sequence<Pair<String, InputStream>>) {
  ZipOutputStream(targetZipFile.outputStream()).use { zipOut ->
    filesToZip
      .sortedBy { (path, _) -> path } // reproducibile order of file in the archive
      .forEach { (path, content) ->
        zipOut.putNextEntry(ZipEntry(path).apply {
          time = 0 // reproducible timestamp
        })
        content.use { it.copyTo(zipOut) }
        zipOut.closeEntry()
      }
  }
}

fun gz(targetGzFile: Path, fileToPack: Path) {
  fileToPack.inputStream().use { content ->
    GZIPOutputStream(targetGzFile.outputStream()).use { gzOut ->
      content.copyTo(gzOut)
    }
  }
}

/**
 * Extracts a ZIP archive to a specified output path. Output files will preserve symlinks and file permissions from an archive.
 *
 * Warning: to preserve necessary meta-data, we use [ZipFile] instead of [ZipInputStream] or [ZipArchiveInputStream],
 * due to limitations of latter ([source](https://stackoverflow.com/questions/20220256/unzip-symlinks-from-a-ziparchiveinputstream-on-android)).
 * This might result in performance drawbacks. Also, it's not recommended to use this method to extract large archives.
 * Please use it with caution.
 */
fun extractZip(
  archive: Path,
  destination: Path,
  stripTopLevelFolder: Boolean,
  cleanDestination: Boolean,
  temporaryDir: Path,
  logger: Logger,
) = extract(archive, destination, stripTopLevelFolder, cleanDestination, ArchiveType.ZIP, "", temporaryDir, logger)

/**
 * Extracts a single file from a ZIP archive to a specified output path. The method assumes
 * that the ZIP archive contains a single file and will log an error if directories or multiple
 * files do not match the specified criteria.
 *
 * @param archive the path to the ZIP archive to be extracted
 * @param outputFile the path where the extracted file will be written
 * @param logger the logger instance used to log messages during the extraction process
 * @param fileMatcher a predicate used to match the specific file to be extracted
 */
fun extractSingleFileZip(archive: Path, outputFile: Path, logger: Logger, fileMatcher: (ZipEntry) -> Boolean) {
  logger.info("Extracting '$archive' to '$outputFile'")
  // Ensure destination directory exists
  if (Files.notExists(outputFile.parent)) {
    Files.createDirectories(outputFile.parent)
  }

  ZipInputStream(archive.inputStream()).use { zipInputStream ->
    var entry = zipInputStream.nextEntry
    if (entry == null) {
      error("No entry found in '$archive', while expecting at least a single file")
    }
    while (entry != null) {
      if (entry.isDirectory) {
        error("Directory found in '$archive', while expecting a plain file archive")
      }
      if (fileMatcher(entry)) {
        outputFile.outputStream().use { output ->
          zipInputStream.copyTo(output)
        }
        zipInputStream.closeEntry()
        break
      }

      zipInputStream.closeEntry()
      entry = zipInputStream.nextEntry
    }
  }
}


fun extractGz(
  archive: Path,
  destinationFile: Path,
  logger: Logger,
) {
  logger.info("Extracting '$archive' to '$destinationFile'")

  if (destinationFile.parent.notExists()) {
    destinationFile.parent.createDirectories()
  }

  GZIPInputStream(archive.inputStream()).use { gzipIn ->
    destinationFile.outputStream().use { out ->
      gzipIn.copyTo(out)
    }
  }
}

fun extractTarZst(
  archive: Path,
  destination: Path,
  stripTopLevelFolder: Boolean,
  cleanDestination: Boolean,
  temporaryDir: Path,
  logger: Logger,
) = extractCompressedTar(archive, destination, stripTopLevelFolder, cleanDestination, CompressorStreamFactory.ZSTANDARD, temporaryDir, logger)

fun extractTarGz(
  archive: Path,
  destination: Path,
  stripTopLevelFolder: Boolean,
  cleanDestination: Boolean,
  temporaryDir: Path,
  logger: Logger,
  encoding: String? = null,
) = extractCompressedTar(archive, destination, stripTopLevelFolder, cleanDestination, CompressorStreamFactory.GZIP, temporaryDir, logger, encoding)

fun extractTarXz(
  archive: Path,
  destination: Path,
  stripTopLevelFolder: Boolean,
  cleanDestination: Boolean,
  temporaryDir: Path,
  logger: Logger,
  encoding: String? = null,
) = extractCompressedTar(archive, destination, stripTopLevelFolder, cleanDestination, CompressorStreamFactory.XZ, temporaryDir, logger, encoding)


private fun extractCompressedTar(
  archive: Path,
  destination: Path,
  stripTopLevelFolder: Boolean,
  cleanDestination: Boolean,
  compressorName: String,
  temporaryDir: Path,
  logger: Logger,
  encoding: String? = null,
) = extract(archive, destination, stripTopLevelFolder, cleanDestination, ArchiveType.TAR, compressorName, temporaryDir, logger, encoding)

@OptIn(ExperimentalPathApi::class)
private fun extract(
  archive: Path,
  destination: Path,
  stripTopLevelFolder: Boolean,
  cleanDestination: Boolean,
  archiveType: ArchiveType,
  compressorName: String,
  temporaryDir: Path,
  logger: Logger,
  encoding: String? = null,
) {
  val tmpFolder = temporaryDir.resolve("${archive.fileName}_extracted")
  tmpFolder.deleteRecursively()
  tmpFolder.createDirectories()
  logger.info("Extracting '$archive' to '$tmpFolder'")

  when (archiveType) {
    ArchiveType.ZIP -> {
      ZipFile.builder().setPath(archive).get()
        .use { zipFile ->
          zipFile.entries.asSequence().forEach { entry ->
            extractEntry(
              entry = entry,
              destination = tmpFolder,
              stripTopLevelFolder = stripTopLevelFolder,
              isSymbolicLink = entry.isUnixSymlink,
              unixMode = entry.unixMode,
              symLink = zipFile.getUnixSymlink(entry),
              logger = logger,
              entryInputStreamProducer = { zipFile.getInputStream(entry).buffered() },
            )
          }
        }
    }
    ArchiveType.TAR ->
      archive.inputStream().buffered().use { bufferedInputStream ->
        when (compressorName) {
          CompressorStreamFactory.ZSTANDARD -> ZstdCompressorInputStream(bufferedInputStream)
          CompressorStreamFactory.XZ -> XZCompressorInputStream(bufferedInputStream)
          else -> CompressorStreamFactory().createCompressorInputStream(compressorName, bufferedInputStream)
        }.use { `in` ->
          TarArchiveInputStream(`in`, encoding).use { archiveInputStream ->
            archiveInputStream.extractEntriesTo(stripTopLevelFolder, tmpFolder, logger)
          }
        }
      }
  }
  logger.info("Extracted '$archive' to '$tmpFolder'")
  logger.info("Moving '$tmpFolder' to '$destination'")
  when {
    cleanDestination -> destination.deleteRecursively()
    else -> require(!destination.exists() || destination.isDirectory()) { "destination be a directory is it exists" }
  }
  destination.parent.createDirectories()
  tmpFolder.copyToRecursively(destination, followLinks = false, overwrite = cleanDestination)
  tmpFolder.deleteRecursively()
  logger.info("Moved '$tmpFolder' to '$destination'")
}


private fun TarArchiveInputStream.extractEntriesTo(stripTopLevelFolder: Boolean, destination: Path, logger: Logger) {
  val archiveInputStream = this
  var entry = archiveInputStream.nextEntry
  while (entry != null) {
    extractEntry(
      entry = entry,
      destination = destination,
      stripTopLevelFolder = stripTopLevelFolder,
      isSymbolicLink = entry.isSymbolicLink,
      symLink = entry.linkName,
      unixMode = entry.mode,
      logger = logger
    ) { archiveInputStream }
    entry = archiveInputStream.nextEntry
  }
}

/**
 * Extracts a given archive entry to a specified destination path, applying optional transformations
 * such as stripping the top-level folder, handling symbolic links, and transferring file permissions.
 */
private fun extractEntry(
  entry: ArchiveEntry, destination: Path,
  stripTopLevelFolder: Boolean,
  isSymbolicLink: Boolean,
  symLink: String?,
  unixMode: Int,
  logger: Logger,
  entryInputStreamProducer: () -> InputStream,
) {
  val relative = when {
    stripTopLevelFolder -> entry.name.split("/").drop(1).joinToString("/")
    else -> entry.name
  }
  if (relative != "") {
    val destinationFile = destination.resolve(relative).normalize()
    val parent = destinationFile.parent
    val realParent = when {
      parent.exists(LinkOption.NOFOLLOW_LINKS) && parent.isSymbolicLink() -> parent.parent.resolve(parent.readSymbolicLink())
      else -> parent
    }
    realParent.createDirectories()

    when {
      entry.isDirectory -> if (!destinationFile.exists()) {
        destinationFile.createDirectories()
      }
      isSymbolicLink -> {
        requireNotNull(symLink) { "symLink must not be null when isSymbolicLink is true" }
        destinationFile.createSymbolicLinkPointingTo(Path.of(symLink))
      }
      else -> destinationFile.outputStream().use { fileOut ->
        entryInputStreamProducer().copyTo(fileOut)
      }
    }

    restorePermissions(destinationFile, unixMode, isSymbolicLink, logger)
  }
}

private fun restorePermissions(destinationFile: Path, unixMode: Int, isSymbolicLink: Boolean, logger: Logger) {
  when {
    isSymbolicLink -> {}
    unixMode == 0 -> {}
    else -> try {
      val attr = destinationFile.fileAttributesView<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS)
      attr.setPermissions(unixMode.toPosixPermissions())
    }
    catch (_: UnsupportedOperationException) {
      logger.debug("Could not restore permissions on {}, file system is not POSIX compliant", destinationFile)
    }
  }
}

/**
 * Compresses a given [source] folder to an [outputFile] in ZIP format.
 */
fun zip(
  source: Path,
  outputFile: Path,
  withTopLevelFolder: Boolean,
  temporaryDir: Path,
  logger: Logger,
  reproducibilityMode: ReproducibilityMode,
) = compress(source, outputFile, withTopLevelFolder, ArchiveType.ZIP, compressorName = "", reproducibilityMode, temporaryDir, logger)

fun tarGz(
  source: Path,
  outputFile: Path,
  withTopLevelFolder: Boolean,
  temporaryDir: Path,
  logger: Logger,
  reproducibilityMode: ReproducibilityMode,
) = tarWithCompression(source, outputFile, withTopLevelFolder, CompressorStreamFactory.GZIP, reproducibilityMode, temporaryDir, logger)

fun tarZst(
  source: Path,
  outputFile: Path,
  withTopLevelFolder: Boolean,
  temporaryDir: Path,
  logger: Logger,
  reproducibilityMode: ReproducibilityMode,
) = tarWithCompression(source, outputFile, withTopLevelFolder, CompressorStreamFactory.ZSTANDARD, reproducibilityMode, temporaryDir, logger)

private fun tarWithCompression(
  source: Path,
  outputFile: Path,
  withTopLevelFolder: Boolean,
  compressorName: String,
  reproducibilityMode: ReproducibilityMode,
  temporaryDir: Path,
  logger: Logger,
): Path = compress(source, outputFile, withTopLevelFolder, ArchiveType.TAR, compressorName, reproducibilityMode, temporaryDir, logger)

@OptIn(ExperimentalPathApi::class)
private fun compress(
  source: Path,
  outputFile: Path,
  withTopLevelFolder: Boolean,
  archiveType: ArchiveType,
  compressorName: String,
  reproducibilityMode: ReproducibilityMode,
  temporaryDir: Path,
  logger: Logger,
): Path {
  val tmpArchive = temporaryDir.resolve("tmp_${outputFile.fileName}")
  tmpArchive.deleteRecursively()
  if (temporaryDir.notExists()) {
    temporaryDir.createDirectories()
  }

  logger.info("Compressing '$source' to '$tmpArchive'...")
  tmpArchive.outputStream().buffered().use { bufferedOutputStream ->
    val compressorStream = when (archiveType) {
      ArchiveType.ZIP -> bufferedOutputStream // zip uses the default compression method, no need to wrap it
      ArchiveType.TAR -> when (compressorName) {
        CompressorStreamFactory.ZSTANDARD -> ZstdCompressorOutputStream(bufferedOutputStream)
        CompressorStreamFactory.XZ -> XZCompressorOutputStream(bufferedOutputStream)
        else -> CompressorStreamFactory().createCompressorOutputStream(compressorName, bufferedOutputStream)
      }
    }

    compressorStream.use { out ->
      val archiveStream = when (archiveType) {
        ArchiveType.ZIP -> ZipArchiveOutputStream(out)
        ArchiveType.TAR -> TarArchiveOutputStream(out).apply { setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU) }
      } as ArchiveOutputStream<ArchiveEntry>

      archiveStream.use { outputStream ->
        when {
          source.isDirectory() -> {
            val base = when {
              withTopLevelFolder -> {
                outputStream.addEntry(source, source.name, archiveType, reproducibilityMode)
                "${source.name}/"
              }

              else -> "./"
            }

            val fileTree = when (reproducibilityMode) { // must not follow symlinks
              is ReproducibilityMode.None -> source.walk()
              is ReproducibilityMode.Reproducible -> source.walk().sortedBy { path -> path }
            }

            fileTree.forEach {
              outputStream.addEntry(
                it,
                "$base${it.relativeTo(source).invariantSeparatorsPathString}",
                archiveType,
                reproducibilityMode,
              )
            }
          }

          else -> outputStream.addEntry(source, source.name, archiveType, reproducibilityMode)
        }
      }
    }
  }
  logger.info("Compressed '$source' to '$tmpArchive'")
  logger.info("Moving '$tmpArchive' to '$outputFile'")
  outputFile.deleteRecursively()
  outputFile.parent.createDirectories()
  tmpArchive.moveTo(outputFile)
  logger.info("Moved '$tmpArchive' to '$outputFile'")
  return outputFile
}

private fun ArchiveOutputStream<ArchiveEntry>.addEntry(path: Path, relativePathInArchive: String, archiveType: ArchiveType, reproducibilityMode: ReproducibilityMode) {
  when {
    path.isSymbolicLink() -> {
      when (archiveType) {
        ArchiveType.TAR -> {
          val entry = TarArchiveEntry(relativePathInArchive, LF_SYMLINK).also {
            it.resetLastModifiedTime(reproducibilityMode)
            it.linkName = path.readSymbolicLink().pathString
            it.setFilePermissions(path, reproducibilityMode, LinkOption.NOFOLLOW_LINKS)
          }
          putArchiveEntry(entry)
          closeArchiveEntry()
        }
        ArchiveType.ZIP -> {
          val link = path.readSymbolicLink().pathString
          val entry = ZipArchiveEntry(relativePathInArchive).also {
            it.resetLastModifiedTime(reproducibilityMode)
            it.setFilePermissions(path, reproducibilityMode, UnixStat.LINK_FLAG, LinkOption.NOFOLLOW_LINKS)
            it.size = link.toByteArray().size.toLong()
          }
          putArchiveEntry(entry)
          write(link.toByteArray())
          closeArchiveEntry()
        }
      }
    }

    path.isDirectory() -> {} // this must be checked AFTER `path.isSymbolicLink()`, otherwise symlink pointing to directory will be ignored

    else -> path.inputStream().use { fileIn ->
      val entry: ArchiveEntry = when (archiveType) {
        ArchiveType.TAR -> TarArchiveEntry(path, relativePathInArchive).also {
          it.resetLastModifiedTime(reproducibilityMode)
          it.setFilePermissions(path, reproducibilityMode)
        }
        ArchiveType.ZIP -> ZipArchiveEntry(path, relativePathInArchive).also {
          it.resetLastModifiedTime(reproducibilityMode)
          it.setFilePermissions(path, reproducibilityMode)
        }
      }
      putArchiveEntry(entry)
      fileIn.copyTo(this)
      closeArchiveEntry()
    }
  }
}

private fun TarArchiveEntry.setFilePermissions(path: Path, reproducibilityMode: ReproducibilityMode, vararg linkOption: LinkOption) {
  when (val permissions = path.getFilePermissions(*linkOption)) {
    FilePermissions.Other -> {}
    is FilePermissions.Posix -> when (reproducibilityMode) {
      is ReproducibilityMode.None ->  // Store real file permissions
        mode = permissions.toInt()
      is ReproducibilityMode.Reproducible -> when (val permissionOption = reproducibilityMode.permissionOption) {
        is PermissionOption.Override ->
          mode = permissionOption.permissions.toInt()
        PermissionOption.Preserve ->
          mode = permissions.toInt()
      }
    }
  }
}

private fun ZipArchiveEntry.setFilePermissions(path: Path, reproducibilityMode: ReproducibilityMode, additionalFlags: Int? = null, vararg linkOption: LinkOption) {
  when (val filePermissions = path.getFilePermissions(*linkOption)) {
    FilePermissions.Other -> {}
    is FilePermissions.Posix -> when (reproducibilityMode) {
      is ReproducibilityMode.None -> // Store real file permissions
        storePermissions(additionalFlags, filePermissions.permissions)
      is ReproducibilityMode.Reproducible -> when (val permissionOption = reproducibilityMode.permissionOption) {
        PermissionOption.Preserve -> // Store real file permissions
          storePermissions(additionalFlags, filePermissions.permissions)
        is PermissionOption.Override -> // Store artificial file permissions that make the file reproducible
          storePermissions(additionalFlags, permissionOption.permissions)
      }
    }
  }
}

private fun ZipArchiveEntry.storePermissions(additionalFlags: Int? = null, permissions: Set<PosixFilePermission>) {
  unixMode = when {
    additionalFlags != null -> permissions.toInt() or additionalFlags
    else -> permissions.toInt()
  }
}

private fun TarArchiveEntry.resetLastModifiedTime(reproducibilityMode: ReproducibilityMode) {
  when (reproducibilityMode) {
    is ReproducibilityMode.None -> {}
    is ReproducibilityMode.Reproducible -> {
      lastModifiedTime = FileTime.fromMillis(reproducibilityMode.lastModifiedTime)
    }
  }
}

private fun ZipArchiveEntry.resetLastModifiedTime(reproducibilityMode: ReproducibilityMode) {
  when (reproducibilityMode) {
    is ReproducibilityMode.None -> {}
    is ReproducibilityMode.Reproducible -> {
      lastModifiedTime = FileTime.fromMillis(reproducibilityMode.lastModifiedTime)
    }
  }
}


private fun Path.getFilePermissions(vararg options: LinkOption): FilePermissions {
  return try {
    Posix(getPosixFilePermissions(*options))
  }
  catch (_: UnsupportedOperationException) {
    FilePermissions.Other
  }
}

internal fun Int.toPosixPermissions(): Set<PosixFilePermission> {
  val perms = mutableSetOf<PosixFilePermission>()
  if (this and 0b100000000 != 0) perms.add(PosixFilePermission.OWNER_READ)
  if (this and 0b010000000 != 0) perms.add(PosixFilePermission.OWNER_WRITE)
  if (this and 0b001000000 != 0) perms.add(PosixFilePermission.OWNER_EXECUTE)
  if (this and 0b000100000 != 0) perms.add(PosixFilePermission.GROUP_READ)
  if (this and 0b000010000 != 0) perms.add(PosixFilePermission.GROUP_WRITE)
  if (this and 0b000001000 != 0) perms.add(PosixFilePermission.GROUP_EXECUTE)
  if (this and 0b000000100 != 0) perms.add(PosixFilePermission.OTHERS_READ)
  if (this and 0b000000010 != 0) perms.add(PosixFilePermission.OTHERS_WRITE)
  if (this and 0b000000001 != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE)
  return perms
}

internal fun Set<PosixFilePermission>.toInt(): Int =
  map { permission ->
    when (permission) {
      PosixFilePermission.OWNER_READ -> 0b100000000
      PosixFilePermission.OWNER_WRITE -> 0b010000000
      PosixFilePermission.OWNER_EXECUTE -> 0b001000000
      PosixFilePermission.GROUP_READ -> 0b000100000
      PosixFilePermission.GROUP_WRITE -> 0b000010000
      PosixFilePermission.GROUP_EXECUTE -> 0b000001000
      PosixFilePermission.OTHERS_READ -> 0b000000100
      PosixFilePermission.OTHERS_WRITE -> 0b000000010
      PosixFilePermission.OTHERS_EXECUTE -> 0b000000001
    }
  }.sum()

private sealed class FilePermissions {
  object Other : FilePermissions()
  data class Posix(val permissions: Set<PosixFilePermission>) : FilePermissions() {

    fun toInt() = permissions.toInt()
  }
}

private enum class ArchiveType {
  ZIP,
  TAR,
}

/**
 * Enum representing modes of reproducibility for file compression operations.
 *
 * Reproducibility is a measure of ensuring consistent and predictable outcomes when creating compressed archives.
 * Depending on the selected mode, adjustments such as sorting file entries, normalizing file attributes,
 * or resetting file modification timestamps may be applied during compression to ensure the output archive
 * remains consistent across different environments and execution runs.
 */
sealed class ReproducibilityMode {

  /**
   * Represents the mode where no changes or adjustments are made to ensure reproducibility.
   * Files and directories are processed and compressed as they are, preserving their original attributes.
   */
  data object None : ReproducibilityMode()

  /**
   * Represents the mode where adjustments are made to ensure reproducible outcomes.
   * Files and directories are processed and compressed in a way that should ensure consistent and predictable outcomes across different environments and execution runs.
   *
   * @param permissionOption an option specifying how file permissions should be handled during compression. Defaults to [PermissionOption.Preserve].
   *   - [PermissionOption.Preserve]: preserves the original file permissions.
   *   This option might not always give a reproducible results, since posix file permissions are different from windows ones,
   *   and will probably be lost during compression of the same files on Windows.
   *   - [PermissionOption.Override]: overrides the original file permissions with the specified ones to have consistent results.
   * @param lastModifiedTime last modification timestamp to apply to files and directories.
   *
   * Please make sure that all the parameters are consistent across different environments and execution runs.
   */
  data class Reproducible(
    val permissionOption: PermissionOption,
    val lastModifiedTime: Long = 0L,
  ) : ReproducibilityMode() {

    sealed class PermissionOption {
      data object Preserve : PermissionOption()
      data class Override(val permissions: Set<PosixFilePermission> = defaultPermissions) : PermissionOption()

      companion object {
        val defaultPermissions: Set<PosixFilePermission> = //Default permissions for reproducible archives, 0755
          setOf(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE,
          )
      }
    }
  }
}