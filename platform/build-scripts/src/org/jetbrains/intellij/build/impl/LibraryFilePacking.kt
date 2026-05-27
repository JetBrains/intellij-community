// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.jps.model.library.JpsLibrary
import java.nio.file.Path

private val JAR_NAME_WITH_VERSION_PATTERN = "(.*)-\\d+(?:\\.\\d+)*\\.jar*".toPattern()

@Internal
fun removeVersionFromJar(fileName: String): String {
  val matcher = JAR_NAME_WITH_VERSION_PATTERN.matcher(fileName)
  return if (matcher.matches()) "${matcher.group(1)}.jar" else fileName
}

@Internal
fun nameToJarFileName(name: String): String = sanitizeFileName(name.lowercase(), replacement = "-") { it == ' ' } + ".jar"

private val agentLibrariesNotForcedInSeparateJars = listOf(
  "ideformer",
  "code-agents",
  "code-prompt-agents"
)

@Internal
fun isSeparateLibraryJar(fileName: String): Boolean {
  return fileName.endsWith("-rt.jar") ||
         fileName.startsWith("byte-buddy-") ||
         fileName.startsWith("objenesis-") ||
         (fileName.contains("-agent") && agentLibrariesNotForcedInSeparateJars.none { fileName.contains(it) }) ||
         fileName.startsWith("maven-")
}

@Internal
class LibraryFileCopyTracker {
  private val copiedFiles = HashSet<CopiedForKey>()

  fun markLibraryFileForCopy(file: Path, targetFile: Path?): Boolean {
    return copiedFiles.add(CopiedForKey(file, targetFile))
  }

  fun getLibraryFiles(library: JpsLibrary, targetFile: Path?, outputProvider: ModuleOutputProvider): MutableList<Path> {
    val files = getLibraryRoots(library, outputProvider).toMutableList()
    val iterator = files.iterator()
    while (iterator.hasNext()) {
      val file = iterator.next()
      // Allow the same source file in different target files, but skip duplicate copies to the same target.
      if (!markLibraryFileForCopy(file = file, targetFile = targetFile)) {
        iterator.remove()
      }
    }
    return files
  }
}

// null targetFile means main jar
private data class CopiedForKey(@JvmField val file: Path, @JvmField val targetFile: Path?)
