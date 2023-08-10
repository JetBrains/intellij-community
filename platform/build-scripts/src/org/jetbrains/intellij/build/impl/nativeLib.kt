// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.HashMapZipFile
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal fun CoroutineScope.packNativePresignedFiles(nativeFiles: Map<ZipSource, List<String>>, dryRun: Boolean, context: BuildContext) {
  for ((source, paths) in nativeFiles) {
    val sourceFile = source.file
    launch(Dispatchers.IO) {
      unpackNativeLibraries(sourceFile = sourceFile, paths = paths, dryRun = dryRun, context = context)
    }
  }
}

private suspend fun unpackNativeLibraries(sourceFile: Path, paths: List<String>, dryRun: Boolean, context: BuildContext) {
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
    val outDir = context.paths.tempDir.resolve(libName)
    Files.createDirectories(outDir)
    for (pathWithPackage in paths) {
      val path = pathWithPackage.substring(packagePrefix.length)
      val fileName = path.substring(path.lastIndexOf('/') + 1)

      val os = when {
        osNameStartsWith(path, "darwin") || osNameStartsWith(path, "mac") || osNameStartsWith(path, "macos") -> OsFamily.MACOS
        path.startsWith("win32-") || osNameStartsWith(path, "win") || osNameStartsWith(path, "windows") -> OsFamily.WINDOWS
        path.startsWith("Linux-Android/") || path.startsWith("Linux-Musl/") -> continue
        osNameStartsWith(path, "linux") -> OsFamily.LINUX
        else -> continue
      }

      if (!allPlatformsRequired && !context.options.targetOs.contains(os) && signTool.signNativeFileMode != SignNativeFileMode.PREPARE) {
        continue
      }

      val osAndArch = path.substring(0, path.indexOf('/'))
      val arch: JvmArchitecture? = when {
        osAndArch.endsWith("-aarch64") || path.contains("/aarch64/") -> JvmArchitecture.aarch64
        path.contains("x86-64") || path.contains("x86_64") -> JvmArchitecture.x64
        // universal library
        os == OsFamily.MACOS && path.count { it == '/' } == 1 -> null
        !osAndArch.contains('-') && path.count { it == '/' } == 1 -> JvmArchitecture.x64
        else -> {
          continue
        }
      }

      if (!allPlatformsRequired && arch != null &&
          (context.options.targetArch != null && context.options.targetArch != arch) &&
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
        file = outDir.resolve(path)!!
        if (!dryRun) {
          Files.createDirectories(file.parent)
          FileChannel.open(file, W_CREATE_NEW).use { channel ->
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

        if (os != OsFamily.LINUX) {
          unsignedFiles.computeIfAbsent(os) { mutableListOf() }.add(file)
        }
      }

      val relativePath = "lib/$libName/" + getRelativePath(libName, arch, fileName, path)
      val distFile = DistFile(file = file, relativePath = relativePath,
                              os = os.takeUnless { allPlatformsRequired },
                              arch = arch.takeUnless { allPlatformsRequired })
      context.addDistFile(distFile)
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
  return path.startsWith("${prefix}-", ignoreCase = true) || path.startsWith("${prefix}/", ignoreCase = true)
}
