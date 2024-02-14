// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.HashMapZipFile
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.NativeFileArchitecture.*
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

@VisibleForTesting
object OsFamilyDetector {
  private val regex = "(^|/)((?<macos>(darwin|mac|macos)[-/])|(?<win>win32-|(win|windows)[-/])|(?<android>Linux-(Android|Musl)/)|(?<linux>linux[-/]))".toRegex(RegexOption.IGNORE_CASE)

  fun detectOsFamily(path: String): Pair<OsFamily, Int>? {
    val result = regex.find(path) ?: return null
    return result.groups["macos"]?.run { OsFamily.MACOS to range.first }
           ?: result.groups["win"]?.run { OsFamily.WINDOWS to range.first }
           ?: result.groups["linux"]?.run { OsFamily.LINUX to range.first }
  }
}

@VisibleForTesting
class NativeFilesMatcher(paths: List<String>, private val targetOs: Iterable<OsFamily>?, private val targetArch: JvmArchitecture?) {
  private val iterator = paths.iterator()
  var commonPathPrefix: CharSequence? = null

  fun findNext(): Match? {
    while (iterator.hasNext()) {
      val pathWithPrefix = iterator.next()
      val osAndIndex = OsFamilyDetector.detectOsFamily(pathWithPrefix) ?: continue
      val prefix = commonPathPrefix?.apply {
        assert(length == osAndIndex.second) {
          "All native runtimes should have common path prefix. Failed to match $pathWithPrefix to $this"
        }
      } ?: pathWithPrefix.subSequence(startIndex = 0, endIndex = osAndIndex.second).also { commonPathPrefix = it }
      val osFamily = osAndIndex.first
      if (targetOs != null && !targetOs.contains(osFamily)) {
        continue
      }

      val path = pathWithPrefix.substring(prefix.length)
      val nativeFileArchitecture = determineArch(os = osFamily, path = path) ?: continue
      if (targetArch != null && !nativeFileArchitecture.compatibleWithTarget(targetArch)) {
        continue
      }

      return Match(pathWithPrefix = pathWithPrefix, path = path, osFamily = osFamily, arch = nativeFileArchitecture.jvmArch)
    }
    return null
  }

  data class Match(
    @JvmField val pathWithPrefix: String,
    @JvmField val path: String,
    @JvmField val osFamily: OsFamily,
    @JvmField val arch: JvmArchitecture?,
  ) {
    override fun toString(): String = "$pathWithPrefix, path=$path, os=$osFamily, arch=$arch"
  }
}

private val posixExecutableFileAttribute = PosixFilePermissions.asFileAttribute(EnumSet.of(
  PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
  PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
  PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
))

internal suspend fun packNativePresignedFiles(
  nativeFiles: Map<ZipSource, List<String>>,
  dryRun: Boolean,
  context: BuildContext,
  toRelativePath: (String, String) -> String,
) {
  coroutineScope {
    for ((source, paths) in nativeFiles) {
      val sourceFile = source.file
      launch(Dispatchers.IO) {
        unpackNativeLibraries(
          sourceFile = sourceFile,
          paths = paths,
          dryRun = dryRun,
          context = context,
          toRelativePath = toRelativePath,
        )
      }
    }
  }
}

private suspend fun unpackNativeLibraries(
  sourceFile: Path,
  paths: List<String>,
  dryRun: Boolean,
  context: BuildContext,
  toRelativePath: (String, String) -> String,
) {
  val libVersion = sourceFile.getName(sourceFile.nameCount - 2).toString()
  val signTool = context.proprietaryBuildTools.signTool
  val unsignedFiles = TreeMap<OsFamily, MutableList<Path>>()

  val libName = getLibNameBySourceFile(sourceFile)
  // we need to keep async-profiler agents for all platforms to support remote target profiling,
  // as a suitable agent is copied to a remote machine
  val allPlatformsRequired = libName == "async-profiler"
  val targetOs: Collection<OsFamily>?
  val targetArch: JvmArchitecture?
  if (!allPlatformsRequired && signTool.signNativeFileMode != SignNativeFileMode.PREPARE) {
    targetOs = context.options.targetOs
    targetArch = context.options.targetArch
  }
  else {
    targetOs = null
    targetArch = null
  }

  val nativeFileMatcher = NativeFilesMatcher(paths, targetOs, targetArch)
  HashMapZipFile.load(sourceFile).use { zipFile ->
    val tempDir = context.paths.tempDir.resolve(libName)
    Files.createDirectories(tempDir)
    while (true) {
      val match = nativeFileMatcher.findNext() ?: break
      val pathWithPackage = match.pathWithPrefix
      val path = match.path
      val fileName = path.substring(path.lastIndexOf('/') + 1)
      val os = match.osFamily
      val arch = match.arch

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

      context.addDistFile(DistFile(
        content = LocalDistFileContent(file),
        relativePath = toRelativePath(libName, getRelativePath(libName = libName, arch = arch, fileName = fileName, path = path)),
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
private enum class NativeFileArchitecture(@JvmField val jvmArch: JvmArchitecture?) {
  X_64(JvmArchitecture.x64),
  AARCH_64(JvmArchitecture.aarch64),
  // universal native file can be used by any platform
  UNIVERSAL(null);

  fun compatibleWithTarget(targetArch: JvmArchitecture?): Boolean {
    return targetArch == null || this == UNIVERSAL || targetArch == jvmArch
  }
}

@Suppress("SpellCheckingInspection")
private fun determineArch(os: OsFamily, path: CharSequence): NativeFileArchitecture? {
  // detect architecture from subfolders e.g. "linux-aarch64/libsqliteij.so"
  val osAndArch = path.indexOf('/').takeIf { it != -1 }?.let { path.subSequence(0, it) } ?: return null
  return when {
    osAndArch.endsWith("-aarch64") || path.contains("/aarch64/") -> AARCH_64
    path.contains("x86-64") || path.contains("x86_64") -> X_64
    os == OsFamily.MACOS && path.count { it == '/' } == 1 -> UNIVERSAL
    !osAndArch.contains('-') && path.count { it == '/' } == 1 -> X_64
    else -> null
  }
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
