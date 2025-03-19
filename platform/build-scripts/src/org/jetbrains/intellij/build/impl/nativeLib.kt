// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.intellij.util.PathUtilRt
import com.intellij.util.lang.ZipFile
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
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

/**
 * Set of native files that shouldn't be signed.
 */
@Suppress("SpellCheckingInspection")
private val nonSignFiles = java.util.Set.of(
  // Native file used by skiko (Compose backend) for Windows.
  // It cannot be signed.
  "icudtl.dat"
)

@VisibleForTesting
object OsFamilyDetector {
  private val macosRegex = "(darwin|mac|macos)".toRegex(RegexOption.IGNORE_CASE)
  private val windowsRegex = "(windows|win32-|win)".toRegex(RegexOption.IGNORE_CASE)
  private val linuxRegex = "linux".toRegex(RegexOption.IGNORE_CASE)

  private val regex = "(^|-|/)((?<macos>(darwin|mac|macos)[-/])|(?<win>win32-|(win|windows)[-/])|(?<android>Linux-(Android|Musl)/)|(?<linux>linux[-/]))".toRegex(RegexOption.IGNORE_CASE)

  fun detectOsFamily(path: String): Pair<OsFamily, String>? {
    if (!path.contains("/")) { // support for dirless natives like skiko
      return when {
        macosRegex.containsMatchIn(path) -> OsFamily.MACOS to ""
        windowsRegex.containsMatchIn(path) -> OsFamily.WINDOWS to ""
        linuxRegex.containsMatchIn(path) -> OsFamily.LINUX to ""
        path == "icudtl.dat" -> OsFamily.WINDOWS to ""
        else -> null
      }
    }

    val result = regex.find(path) ?: return null
    return result.groups["macos"]?.run { OsFamily.MACOS to path.extractPrefix(range) }
           ?: result.groups["win"]?.run { OsFamily.WINDOWS to path.extractPrefix(range) }
           ?: result.groups["linux"]?.run { OsFamily.LINUX to path.extractPrefix(range) }
  }

  private fun String.extractPrefix(range: IntRange): String {
    return subSequence(startIndex = 0, endIndex = range.first).toString()
  }
}

@VisibleForTesting
class NativeFilesMatcher(paths: List<String>, private val targetOs: Iterable<OsFamily>?, private val targetArch: JvmArchitecture?) {
  private val iterator = paths.iterator()
  var commonPathPrefix: CharSequence? = null

  fun findNext(): Match? {
    while (iterator.hasNext()) {
      val pathWithPrefix = iterator.next()
      val (osFamily, newPrefix) = OsFamilyDetector.detectOsFamily(pathWithPrefix) ?: continue

      if (commonPathPrefix != null) {
        assert(commonPathPrefix == newPrefix) {
          "All native runtimes should have common path prefix. Failed to match $pathWithPrefix to $this"
        }
      }

      val prefix = commonPathPrefix ?: newPrefix.also { commonPathPrefix = it }

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

  companion object {
    fun isCompatibleWithTargetPlatform(name: String, os: PersistentList<OsFamily>, arch: JvmArchitecture?): Boolean {
      val fileOs = OsFamilyDetector.detectOsFamily(name)?.first ?: error("Cannot determine native file OS Family")
      val fileArch = determineArch(fileOs, name) ?: error("Cannot determine native file architecture")
      return os.contains(fileOs) && (arch == null || fileArch.compatibleWithTarget(arch))
    }
  }
}

private val posixExecutableFileAttribute = PosixFilePermissions.asFileAttribute(
  EnumSet.of(
    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
  )
)

internal fun CoroutineScope.packNativePresignedFiles(
  nativeFiles: Map<ZipSource, List<String>>,
  dryRun: Boolean,
  context: BuildContext,
  toRelativePath: (String, String) -> String,
) {
  for ((source, paths) in nativeFiles) {
    val sourceFile = source.file
    launch(Dispatchers.IO + CoroutineName("pack native presigned file $sourceFile")) {
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
  ZipFile.load(sourceFile).use { zipFile ->
    val tempDir = context.paths.tempDir.resolve(libName)
    Files.createDirectories(tempDir)
    while (true) {
      val match = nativeFileMatcher.findNext() ?: break
      val pathWithPackage = match.pathWithPrefix
      val path = match.path
      val fileName = path.substring(path.lastIndexOf('/') + 1)
      val os = match.osFamily
      val arch = match.arch

      var file: Path? = if (os == OsFamily.LINUX || fileName in nonSignFiles || signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
        null
      }
      else {
        signTool.getPresignedLibraryFile(path = path, libName = libName, libVersion = libVersion, context = context)
      }

      // add an executable flag for native packaged files without an extension on POSIX OS (as it can be executed directly, opposite to lib)
      val isExecutable = os != OsFamily.WINDOWS && !PathUtilRt.getFileName(path).contains('.')
      if (file == null) {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        file = tempDir.resolve(path)!!
        if (!dryRun) {
          extractFileToDisk(file = file, zipFile = zipFile, pathWithPackage = pathWithPackage, isExecutable = isExecutable)
        }

        if (os != OsFamily.LINUX && fileName !in nonSignFiles) {
          unsignedFiles.computeIfAbsent(os) { mutableListOf() }.add(file)
        }
      }

      context.addDistFile(
        DistFile(
          content = LocalDistFileContent(file = file, isExecutable = isExecutable),
          relativePath = toRelativePath(libName, getRelativePath(libName = libName, arch = arch, fileName = fileName, path = path)),
          os = os.takeUnless { allPlatformsRequired },
          arch = arch.takeUnless { allPlatformsRequired },
        )
      )
    }
  }

  if (signTool.signNativeFileMode == SignNativeFileMode.PREPARE) {
    val versionOption = mapOf(SignTool.LIB_VERSION_OPTION_NAME to libVersion)
    coroutineScope {
      launch(CoroutineName("signing macOS binaries")) {
        unsignedFiles.get(OsFamily.MACOS)?.let {
          signMacBinaries(files = it, context = context, additionalOptions = versionOption, checkPermissions = false)
        }
      }
      launch(CoroutineName("signing Windows binaries")) {
        unsignedFiles.get(OsFamily.WINDOWS)?.let {
          @Suppress("SpellCheckingInspection")
          context.signFiles(
            it, BuildOptions.WIN_SIGN_OPTIONS + versionOption + persistentMapOf(
            "contentType" to "application/x-exe",
            "jsign_replace" to "true"
          )
          )
        }
      }
    }
  }
}

private fun extractFileToDisk(file: Path, zipFile: ZipFile, pathWithPackage: String, isExecutable: Boolean) {
  Files.createDirectories(file.parent)
  when {
    // add an executable flag for native packaged files without an extension on POSIX OS (as it can be executed directly, opposite to lib)
    isExecutable && !isWindows -> FileChannel.open(file, W_CREATE_NEW, posixExecutableFileAttribute)
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
  if (!path.contains("/")) {
    return when {
      path.contains("x64") -> X_64
      path.contains("aarch64") || path.contains("arm64") -> AARCH_64
      path == "icudtl.dat" -> UNIVERSAL
      else -> null
    }
  }

  val osAndArch = path.indexOf('/').takeIf { it != -1 }?.let { path.subSequence(0, it) } ?: return null
  return when {
    osAndArch.endsWith("-aarch64") || path.contains("/aarch64/") || osAndArch.contains("arm64") -> AARCH_64
    path.contains("x86-64") || path.contains("x86_64") || osAndArch.contains("x64") -> X_64
    os == OsFamily.MACOS && path.count { it == '/' } == 1 -> UNIVERSAL
    !osAndArch.contains('-') && path.count { it == '/' } == 1 -> X_64
    else -> null
  }
}

// each library has own implementation of handling path property
private fun getRelativePath(libName: String, arch: JvmArchitecture?, fileName: String, path: String): String {
  when (libName) {
    "async-profiler" -> {
      return if (arch == null) fileName else "${arch.dirName}/$fileName"
    }
    "skiko-awt-runtime-all" -> {
      return fileName
    }
    else -> {
      return if (libName == "jna") "${arch!!.dirName}/$fileName" else path
    }
  }
}

private fun getLibNameBySourceFile(sourceFile: Path): String {
  val fileName = sourceFile.fileName.toString()
  return fileName.split('-').takeWhile { !it.contains('.') }.joinToString(separator = "-")
}
