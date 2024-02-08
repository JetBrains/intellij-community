// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.HashMapZipFile
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.NativeFileArchitecture.*
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

private val posixExecutableFileAttribute = PosixFilePermissions.asFileAttribute(EnumSet.of(
  PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
  PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
  PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
))

internal suspend fun packNativePresignedFiles(
  nativeFiles: Map<ZipSource, List<String>>,
  dryRun: Boolean,
  context: BuildContext,
  outDir: Path,
) {
  coroutineScope {
    for ((source, paths) in nativeFiles) {
      val sourceFile = source.file
      launch(Dispatchers.IO) {
        unpackNativeLibraries(sourceFile = sourceFile, paths = paths, dryRun = dryRun, context = context, outputDir = outDir)
      }
    }
  }
}

private suspend fun unpackNativeLibraries(sourceFile: Path, paths: List<String>, dryRun: Boolean, context: BuildContext, outputDir: Path) {
  val libVersion = sourceFile.getName(sourceFile.nameCount - 2).toString()
  val signTool = context.proprietaryBuildTools.signTool
  val unsignedFiles = TreeMap<OsFamily, MutableList<Path>>()

  val packagePrefix = if (paths.size == 1) {
    // if a native lib is built with the only arch for testing purposes
    val first = paths.first()
    first.substring(0, first.indexOf('/') + 1)
  }
  else {
    getCommonPath(paths)
  }

  val libName = getLibNameBySourceFile(sourceFile)
  // We need to keep async-profiler agents for all platforms to support remote target profiling,
  // as a suitable agent is copied to a remote machine
  val allPlatformsRequired = libName == "async-profiler"

  HashMapZipFile.load(sourceFile).use { zipFile ->
    val tempDir = context.paths.tempDir.resolve(libName)
    Files.createDirectories(tempDir)
    for (pathWithPackage in paths) {
      val path = pathWithPackage.substring(packagePrefix.length)
      val fileName = path.substring(path.lastIndexOf('/') + 1)

      val os = determineOsFamily(path) ?: continue

      if (!allPlatformsRequired && !context.options.targetOs.contains(os) && signTool.signNativeFileMode != SignNativeFileMode.PREPARE) {
        continue
      }

      val nativeFileArchitecture = determineArch(os, path) ?: continue

      if (!allPlatformsRequired &&
          !nativeFileArchitecture.compatibleWithTarget(context.options.targetArch) &&
          signTool.signNativeFileMode != SignNativeFileMode.PREPARE) {
        continue
      }

      var file: Path? = if (os == OsFamily.LINUX || signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
        null
      }
      else {
        signTool.getPresignedLibraryFile(path = path, libName = libName, libVersion = libVersion, context = context)
      }

      if (file == null) {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        file = tempDir.resolve(path)!!
        if (!dryRun) {
          extractFileToDisk(file = file, zipFile = zipFile, pathWithPackage = pathWithPackage)
        }

        if (os != OsFamily.LINUX) {
          unsignedFiles.computeIfAbsent(os) { mutableListOf() }.add(file)
        }
      }

      val arch = nativeFileArchitecture.jvmArch
      val relativePath = outputDir.resolve(libName)
        .resolve(getRelativePath(libName = libName, arch = arch, fileName = fileName, path = path))
      context.addDistFile(DistFile(
        file = file,
        relativePath = relativePath.toString(),
        os = os.takeUnless { allPlatformsRequired },
        arch = arch.takeUnless { allPlatformsRequired },
      ))
    }
  }

  if (signTool.signNativeFileMode == SignNativeFileMode.PREPARE) {
    val versionOption = mapOf(SignTool.LIB_VERSION_OPTION_NAME to libVersion)
    coroutineScope {
      launch {
        unsignedFiles.get(OsFamily.MACOS)?.let {
          signMacBinaries(files = it, context = context, additionalOptions = versionOption, checkPermissions = false)
        }
      }
      launch {
        unsignedFiles.get(OsFamily.WINDOWS)?.let {
          @Suppress("SpellCheckingInspection")
          context.signFiles(it, BuildOptions.WIN_SIGN_OPTIONS + versionOption + persistentMapOf(
            "contentType" to "application/x-exe",
            "jsign_replace" to "true"
          ))
        }
      }
    }
  }
}

private fun extractFileToDisk(file: Path, zipFile: HashMapZipFile, pathWithPackage: String) {
  Files.createDirectories(file.parent)
  when {
    // add an executable flag for native packaged files without an extension on POSIX OS (as it can be executed directly, opposite to lib)
    !isWindows && !file.fileName.toString().contains('.') -> FileChannel.open(file, W_CREATE_NEW, posixExecutableFileAttribute)
    else -> FileChannel.open(file, W_CREATE_NEW)
  }.use { channel ->
    val byteBuffer = zipFile.getByteBuffer(pathWithPackage)!!
    try {
      while (byteBuffer.hasRemaining()) {
        channel.write(byteBuffer)
      }
    }
    finally {
      zipFile.releaseBuffer(byteBuffer)
    }
  }
}

/**
 * Represent CPU architecture for which native code was built.
 */
private enum class NativeFileArchitecture(val jvmArch: JvmArchitecture?) {
  X_64(JvmArchitecture.x64), AARCH_64(JvmArchitecture.aarch64),

  // Universal native file can be used by any platform
  UNIVERSAL(null);

  fun compatibleWithTarget(targetArch: JvmArchitecture?): Boolean {
    return targetArch == null || this == UNIVERSAL || targetArch == jvmArch
  }
}

@Suppress("SpellCheckingInspection")
private fun determineArch(os: OsFamily, path: String): NativeFileArchitecture? {
  // detect architecture from subfolders e.g. "linux-aarch64/libsqliteij.so"
  val osAndArch = path.indexOf('/').takeIf { it != -1 }?.let { path.substring(0, it) } ?: return null
  return when {
    osAndArch.endsWith("-aarch64") || path.contains("/aarch64/") -> AARCH_64
    path.contains("x86-64") || path.contains("x86_64") -> X_64
    os == OsFamily.MACOS && path.count { it == '/' } == 1 -> UNIVERSAL
    !osAndArch.contains('-') && path.count { it == '/' } == 1 -> X_64
    else -> null
  }
}

private fun determineOsFamily(path: String): OsFamily? {
  val osFromPath = when {
    osNameStartsWith(path, "darwin") || osNameStartsWith(path, "mac") || osNameStartsWith(path, "macos") -> OsFamily.MACOS
    path.startsWith("win32-") || osNameStartsWith(path, "win") || osNameStartsWith(path, "windows") -> OsFamily.WINDOWS
    path.startsWith("Linux-Android/") || path.startsWith("Linux-Musl/") -> return null
    osNameStartsWith(path, "linux") -> OsFamily.LINUX
    else -> null
  }

  return osFromPath
}

// each library has own implementation of handling path property
private fun getRelativePath(libName: String, arch: JvmArchitecture?, fileName: String, path: String): String {
  if (libName == "async-profiler") {
    return if (arch == null) fileName else "${arch.dirName}/$fileName"
  }
  else {
    return if (libName == "jna") "${arch!!.dirName}/$fileName" else path
  }
}

private fun getLibNameBySourceFile(sourceFile: Path): String {
  val fileName = sourceFile.fileName.toString()
  return fileName.split('-').takeWhile { !it.contains('.') }.joinToString(separator = "-")
}

private fun osNameStartsWith(path: String, prefix: String): Boolean {
  return path.startsWith("$prefix-", ignoreCase = true) || path.startsWith("$prefix/", ignoreCase = true)
}
