// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.nio.file.Path
import kotlin.io.path.name

private val JAR_NAME_WITH_VERSION_PATTERN = "(.*)-\\d+(?:\\.\\d+)*\\.jar*".toPattern()

@Internal
fun removeVersionFromJar(fileName: String): String {
  val matcher = JAR_NAME_WITH_VERSION_PATTERN.matcher(fileName)
  return if (matcher.matches()) "${matcher.group(1)}.jar" else fileName
}

@Internal
fun nameToJarFileName(name: String): String = sanitizeFileName(name.lowercase(), replacement = "-") { it == ' ' } + ".jar"

/**
 * The name a library is known by in the distribution: its own name, or - for an unnamed (`#`-prefixed) module library - the file name of its
 * single JAR.
 */
@Internal
fun getLibraryFileName(lib: JpsLibrary): String {
  val name = lib.name
  if (name.startsWith('#')) {
    // unnamed module libraries in the IntelliJ project may have only one root
    val paths = lib.getPaths(JpsOrderRootType.COMPILED)
    require(paths.size == 1) {
      "Unnamed module library has more than one element: $paths"
    }
    return paths[0].name
  }
  return name
}

private val agentLibrariesNotForcedInSeparateJars = listOf(
  "code-agents",
  "code-prompt-agents"
)

/**
 * Libraries that have to stay standalone jar files: agents are attached by path at runtime, and `-rt` / `maven-` jars are loaded by
 * external processes. Objenesis is deliberately absent - it is an ordinary library, and hoisting it out of the content module that wraps it
 * left that module's jar empty, so every module depending on the wrapper failed to resolve the classes (IJPL-252372).
 */
@Internal
fun isSeparateLibraryJar(fileName: String): Boolean {
  return fileName.endsWith("-rt.jar") ||
         fileName.startsWith("byte-buddy-") ||
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
