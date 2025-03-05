// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.zip.Deflater

val W_OVERWRITE: EnumSet<StandardOpenOption> =
  EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)

enum class AddDirEntriesMode {
  NONE,
  RESOURCE_ONLY,
  ALL
}

// `createFileParentDirs = false` can be used for performance reasons if you do create a lot of zip files
fun zipWithCompression(
  targetFile: Path,
  dirs: Map<Path, String>,
  compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
  addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE,
  overwrite: Boolean = false,
  createFileParentDirs: Boolean = true,
  fileFilter: ((name: String) -> Boolean)? = null,
) {
  if (createFileParentDirs) {
    Files.createDirectories(targetFile.parent)
  }
  ZipFileWriter(
    channel = FileChannel.open(targetFile, if (overwrite) W_OVERWRITE else W_CREATE_NEW),
    deflater = if (compressionLevel == Deflater.NO_COMPRESSION) null else Deflater(compressionLevel, true),
    zipIndexWriter = ZipIndexWriter(indexWriter = null),
  ).use { zipFileWriter ->
    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      archiveDirToZipWriter(
        zipFileWriter = zipFileWriter,
        fileAdded = if (fileFilter == null) null else { name, _ -> fileFilter(name) },
        dirs = dirs,
      )
    }
    else {
      val dirNameSetToAdd = LinkedHashSet<String>()
      val fileAdded = { name: String, _: Path ->
        if (fileFilter != null && !fileFilter(name)) {
          false
        }
        else {
          if (addDirEntriesMode == AddDirEntriesMode.ALL ||
              (addDirEntriesMode == AddDirEntriesMode.RESOURCE_ONLY &&
               !name.endsWith(".class") && !name.endsWith("/package.html") && name != "META-INF/MANIFEST.MF")) {
            addDirWithParents(name, dirNameSetToAdd)
          }
          true
        }
      }

      archiveDirToZipWriter(zipFileWriter = zipFileWriter, fileAdded = fileAdded, dirs = dirs)
      for (dir in dirNameSetToAdd) {
        zipFileWriter.dir(name = dir)
      }
    }
  }
}

// symlinks are not supported but can be easily implemented - see CollectingVisitor.visitFile
fun zip(
  targetFile: Path,
  dirs: Map<Path, String>,
  addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY,
  overwrite: Boolean = false,
  fileFilter: ((name: String) -> Boolean)? = null,
) {
  Files.createDirectories(targetFile.parent)
  val packageIndexBuilder = if (addDirEntriesMode == AddDirEntriesMode.NONE) null else PackageIndexBuilder()
  ZipFileWriter(
    channel = FileChannel.open(targetFile, if (overwrite) W_OVERWRITE else W_CREATE_NEW),
    zipIndexWriter = ZipIndexWriter(indexWriter = packageIndexBuilder?.indexWriter),
  ).use { zipFileWriter ->
    if (packageIndexBuilder == null) {
      archiveDirToZipWriter(
        zipFileWriter = zipFileWriter,
        fileAdded = if (fileFilter == null) null else { name, _ -> fileFilter(name) },
        dirs = dirs,
      )
    }
    else {
      archiveDirToZipWriter(
        zipFileWriter = zipFileWriter,
        fileAdded = { name, _ ->
          if (fileFilter != null && !fileFilter(name)) {
            false
          }
          else {
            packageIndexBuilder.addFile(name, addClassDir = addDirEntriesMode == AddDirEntriesMode.ALL)
            true
          }
        },
        dirs = dirs,
      )
      packageIndexBuilder.writePackageIndex(writer = zipFileWriter, addDirEntriesMode = addDirEntriesMode)
    }
  }
}

private fun archiveDirToZipWriter(
  zipFileWriter: ZipFileWriter,
  fileAdded: ((String, Path) -> Boolean)?,
  dirs: Map<Path, String>,
) {
  val archiver = ZipArchiver(zipFileWriter, fileAdded)
  for ((dir, prefix) in dirs.entries) {
    val normalizedDir = dir.toAbsolutePath().normalize()
    archiver.setRootDir(normalizedDir, prefix)
    archiveDir(startDir = normalizedDir, addFile = { archiver.addFile(it) })
  }
}

private fun addDirWithParents(name: String, dirNameSetToAdd: MutableSet<String>) {
  var slashIndex = name.lastIndexOf('/')
  if (slashIndex != -1) {
    while (dirNameSetToAdd.add(name.substring(0, slashIndex))) {
      slashIndex = name.lastIndexOf('/', slashIndex - 2)
      if (slashIndex == -1) {
        break
      }
    }
  }
}

class ZipArchiver(private val zipCreator: ZipFileWriter, @JvmField val fileAdded: ((String, Path) -> Boolean)? = null) : AutoCloseable {
  private var localPrefixLength = -1
  private var archivePrefix = ""

  // rootDir must be absolute and normalized
  fun setRootDir(rootDir: Path, prefix: String = "") {
    archivePrefix = when {
      prefix.isNotEmpty() && !prefix.endsWith('/') -> "$prefix/"
      prefix == "/" -> ""
      else -> prefix
    }

    localPrefixLength = rootDir.toString().length + 1
  }

  fun addFile(file: Path) {
    val name = archivePrefix + file.toString().substring(localPrefixLength).replace(File.separatorChar, '/')
    if (fileAdded == null || fileAdded.invoke(name, file)) {
      zipCreator.file(name, file)
    }
  }

  override fun close() {
    zipCreator.close()
  }
}

inline fun archiveDir(startDir: Path, addFile: (file: Path) -> Unit, excludes: List<PathMatcher>? = null) {
  val dirCandidates = ArrayDeque<Path>()
  dirCandidates.add(startDir)
  val tempList = ArrayList<Path>()
  while (true) {
    val dir = dirCandidates.pollFirst() ?: break
    tempList.clear()
    val dirStream = try {
      Files.newDirectoryStream(dir)
    }
    catch (_: NoSuchFileException) {
      continue
    }

    dirStream.use {
      if (excludes == null) {
        tempList.addAll(it)
      }
      else {
        l@ for (child in it) {
          val relative = startDir.relativize(child)
          for (exclude in excludes) {
            if (exclude.matches(relative)) {
              continue@l
            }
          }
          tempList.add(child)
        }
      }
    }

    tempList.sort()
    for (file in tempList) {
      if (Files.isDirectory(file)) {
        dirCandidates.add(file)
      }
      else {
        addFile(file)
      }
    }
  }
}

inline fun writeZipUsingTempFile(file: Path, indexWriter: IkvIndexBuilder?, task: (ZipArchiveOutputStream) -> Unit) {
  writeFileUsingTempFile(file) { tempFile ->
    ZipArchiveOutputStream(
      channel = FileChannel.open(tempFile, WRITE),
      zipIndexWriter = ZipIndexWriter(indexWriter),
    ).use {
      task(it)
    }
  }
}

inline fun writeFileUsingTempFile(file: Path, task: (tempFile: Path) -> Unit) {
  val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
  var moved = false
  try {
    task(tempFile)

    try {
      moveAtomic(tempFile, file)
    }
    catch (e: AccessDeniedException) {
      makeFileWritable(file, e)
      moveAtomic(tempFile, file)
    }
    moved = true
  }
  finally {
    if (!moved) {
      Files.deleteIfExists(tempFile)
    }
  }
}

@PublishedApi
internal fun moveAtomic(from: Path, to: Path) {
  try {
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }
  catch (_: AtomicMoveNotSupportedException) {
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
  }
}

@PublishedApi
internal fun makeFileWritable(file: Path, cause: Throwable) {
  val posixView = Files.getFileAttributeView<PosixFileAttributeView?>(file, PosixFileAttributeView::class.java)
  if (posixView != null) {
    val permissions = posixView.readAttributes().permissions()
    permissions.add(PosixFilePermission.OWNER_WRITE)
    posixView.setPermissions(permissions)
  }

  val dosView = Files.getFileAttributeView<DosFileAttributeView?>(file, DosFileAttributeView::class.java)
  @Suppress("IfThenToSafeAccess")
  if (dosView != null) {
    dosView.setReadOnly(false)
  }

  throw UnsupportedOperationException("Unable to modify file attributes. Unsupported platform.", cause)
}