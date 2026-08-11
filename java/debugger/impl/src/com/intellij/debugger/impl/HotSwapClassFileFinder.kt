// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("HotSwapClassFileFinder")
@file:Suppress("IO_FILE_USAGE", "UsePathInsteadOfFile")

package com.intellij.debugger.impl

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

private data class ExistingClass(
  val classFile: HotSwapClassFile,
  val presentablePath: String,
)

private typealias ExistingClassProvider = (String) -> ExistingClass?

private const val CLASS_EXTENSION = ".class"

@ApiStatus.Internal
fun findExistingClasses(
  classRoots: List<VirtualFile>,
  qualifiedNames: Collection<String>,
  progress: HotSwapProgress,
): Map<String, HotSwapClassFile> {
  if (qualifiedNames.isEmpty() || progress.isCancelled) {
    return emptyMap()
  }

  val result = HashMap<String, HotSwapClassFile>()
  for (root in classRoots) {
    if (progress.isCancelled) {
      break
    }
    if (result.keys.containsAll(qualifiedNames)) {
      break
    }

    val rootPath = root.fileSystem.getNioPath(root)
    if (rootPath != null && Files.isRegularFile(rootPath)) {
      collectExistingClassesFromArchive(rootPath, root.presentableUrl, qualifiedNames, progress, result)
      continue
    }

    collectExistingClasses(qualifiedNames, progress, result, getExistingClassProvider(root))
  }
  return result
}

private fun collectExistingClasses(
  qualifiedNames: Collection<String>,
  progress: HotSwapProgress,
  result: MutableMap<String, HotSwapClassFile>,
  provider: ExistingClassProvider,
) {
  for (qualifiedName in qualifiedNames) {
    if (progress.isCancelled) {
      return
    }
    if (result.containsKey(qualifiedName)) {
      continue
    }

    val relativePath = getClassFileRelativePath(qualifiedName)
    val existingClass = provider(relativePath) ?: continue

    progress.setText(JavaDebuggerBundle.message("progress.hotswap.scanning.path", existingClass.presentablePath))
    result[qualifiedName] = existingClass.classFile
  }
}

private fun getExistingClassProvider(root: VirtualFile): ExistingClassProvider {
  if (root.isDirectory && !root.fileSystem.isReadOnly) {
    return { relativePath ->
      val classFile = File(root.path, relativePath)
      if (!classFile.isFile) {
        null
      }
      else {
        ExistingClass(HotSwapFile(classFile), classFile.path)
      }
    }
  }

  return { relativePath ->
    val classFile = root.findFileByRelativePath(relativePath)
    if (classFile == null || classFile.isDirectory) {
      null
    }
    else {
      val hotSwapFile = createHotSwapFile(classFile)
      if (hotSwapFile != null) ExistingClass(hotSwapFile, classFile.presentableUrl) else null
    }
  }
}

private fun collectExistingClassesFromArchive(
  archive: Path,
  presentableUrl: String,
  qualifiedNames: Collection<String>,
  progress: HotSwapProgress,
  result: MutableMap<String, HotSwapClassFile>,
) {
  try {
    JarFile(archive.toFile()).use { jarFile ->
      collectExistingClasses(qualifiedNames, progress, result, getExistingClassProvider(jarFile, presentableUrl))
    }
  }
  catch (e: IOException) {
    fileLogger().warn("Failed to scan existing classes from archive: $presentableUrl", e)
  }
}

private fun getExistingClassProvider(jarFile: JarFile, presentableUrl: String): ExistingClassProvider {
  return { relativePath ->
    val hotSwapFile = createHotSwapFile(jarFile, relativePath)
    if (hotSwapFile != null) ExistingClass(hotSwapFile, "$presentableUrl!/$relativePath") else null
  }
}

private fun createHotSwapFile(classFile: VirtualFile): HotSwapClassFile? {
  val file = classFile.fileSystem.getNioPath(classFile)
  if (file != null && !Files.isRegularFile(file)) {
    return null
  }
  return HotSwapClassFile.fromVirtualFile(classFile)
}

private fun createHotSwapFile(jarFile: JarFile, relativePath: String): HotSwapClassFile? {
  val entry = jarFile.getJarEntry(relativePath)
  if (entry == null || entry.isDirectory) {
    return null
  }

  val bytes = jarFile.getInputStream(entry).use { it.readAllBytes() }
  return HotSwapClassFile.fromBytes(bytes)
}

private fun getClassFileRelativePath(qualifiedName: String): String = qualifiedName.replace('.', '/') + CLASS_EXTENSION
