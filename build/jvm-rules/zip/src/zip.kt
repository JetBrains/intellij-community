// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.ArrayDeque
import java.util.zip.Deflater

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
    zipWriter(targetFile = targetFile, packageIndexBuilder = null, overwrite = overwrite),
    deflater = if (compressionLevel == Deflater.NO_COMPRESSION) null else Deflater(compressionLevel, true),
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
      zipFileWriter.resultStream.addDirEntries(dirNameSetToAdd)
    }
  }
}

// symlinks are not supported but can be easily implemented - see CollectingVisitor.visitFile
fun zip(
  targetFile: Path,
  dirs: Map<Path, String>,
  addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY,
  overwrite: Boolean = false,
  useCrc: Boolean = true,
  fileFilter: ((name: String) -> Boolean)? = null,
) {
  Files.createDirectories(targetFile.parent)
  if (addDirEntriesMode == AddDirEntriesMode.NONE) {
    ZipFileWriter(zipWriter(targetFile, null, overwrite), useCrc = useCrc).use { zipFileWriter ->
      archiveDirToZipWriter(
        zipFileWriter = zipFileWriter,
        fileAdded = if (fileFilter == null) null else { name, _ -> fileFilter(name) },
        dirs = dirs,
      )
    }
  }
  else {
    doZipWithPackageIndex(
      targetFile = targetFile,
      overwrite = overwrite,
      useCrc = useCrc,
      fileFilter = fileFilter,
      addDirEntriesMode = addDirEntriesMode,
      dirs = dirs,
    )
  }
}

fun zipWithPackageIndex(targetFile: Path, dir: Path) {
  Files.createDirectories(targetFile.parent)

  doZipWithPackageIndex(
    targetFile = targetFile,
    overwrite = false,
    useCrc = true,
    fileFilter = null,
    addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY,
    dirs = java.util.Map.of(dir, ""),
  )
}

private fun doZipWithPackageIndex(
  targetFile: Path,
  overwrite: Boolean,
  useCrc: Boolean,
  fileFilter: ((String) -> Boolean)?,
  addDirEntriesMode: AddDirEntriesMode,
  dirs: Map<Path, String>,
) {
  val packageIndexBuilder = PackageIndexBuilder(addDirEntriesMode)
  ZipFileWriter(zipWriter(targetFile, packageIndexBuilder, overwrite), useCrc = useCrc).use { zipFileWriter ->
    archiveDirToZipWriter(
      zipFileWriter = zipFileWriter,
      fileAdded = { name, _ ->
        if (fileFilter != null && !fileFilter(name)) {
          false
        }
        else {
          packageIndexBuilder.addFile(name)
          true
        }
      },
      dirs = dirs,
    )
  }
}

// visible for tests
fun archiveDirToZipWriter(
  zipFileWriter: ZipFileWriter,
  fileAdded: ((String, Path) -> Boolean)?,
  dirs: Map<Path, String>,
) {
  val archiver = ZipArchiver(fileAdded)
  for ((dir, prefix) in dirs.entries) {
    val normalizedDir = dir.toAbsolutePath().normalize()
    archiver.setRootDir(normalizedDir, prefix)
    archiveDir(startDir = normalizedDir, addFile = { archiver.addFile(it, zipFileWriter) })
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

class ZipArchiver(@JvmField val fileAdded: ((String, Path) -> Boolean)? = null) {
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

  fun addFile(file: Path, zipCreator: ZipFileWriter) {
    @Suppress("IO_FILE_USAGE")
    val name = archivePrefix + file.toString().substring(localPrefixLength).replace(java.io.File.separatorChar, '/')
    if (fileAdded == null || fileAdded.invoke(name, file)) {
      zipCreator.file(name, file)
    }
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

@Suppress("unused")
inline fun writeZipWithoutChecksumUsingTempFile(file: Path, packageIndexBuilder: PackageIndexBuilder?, task: (ZipArchiveOutputStream) -> Unit) {
  writeFileUsingTempFile(file) { tempFile ->
    ZipArchiveOutputStream(
      dataWriter = fileDataWriter(file = tempFile, overwrite = false, isTemp = true),
      zipIndexWriter = ZipIndexWriter(packageIndexBuilder),
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
  val posixView = Files.getFileAttributeView(file, PosixFileAttributeView::class.java)
  if (posixView != null) {
    val permissions = posixView.readAttributes().permissions()
    permissions.add(PosixFilePermission.OWNER_WRITE)
    posixView.setPermissions(permissions)
  }

  val dosView = Files.getFileAttributeView(file, DosFileAttributeView::class.java)
  @Suppress("IfThenToSafeAccess")
  if (dosView != null) {
    dosView.setReadOnly(false)
  }

  throw UnsupportedOperationException("Unable to modify file attributes. Unsupported platform.", cause)
}