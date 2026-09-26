// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModuleReference
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
  "code-agents",
  "code-prompt-agents",
  "jcp-agent-spawner"
)

private val mavenLibrariesNotForcedInSeparateJars = listOf(
  "maven-artifact",
  "maven-central-configuration",
  "maven-plugin-xml-parser",
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
         (fileName.startsWith("maven-") && mavenLibrariesNotForcedInSeparateJars.none { fileName.contains(it) })
}

/**
 * Which library files are already on their way into which target jar.
 *
 * Two libraries of one plugin can name the same artifact. The first one to be packed into a target takes the file, and
 * the second contributes nothing to that target. The decision is made twice: first by jar identity from the project
 * model, so that a library that contributes nothing is never resolved, and then by resolved file for the libraries that
 * are. A dry layout under an explicit Bazel input manifest depends on the first: resolving a library declares it, and
 * the plan declares only the libraries that contribute a file.
 */
@Internal
class LibraryFileCopyTracker {
  private val copiedFiles = HashSet<CopiedForKey>()
  private val copiedIdentities = HashSet<CopiedForKey>()

  fun markLibraryFileForCopy(file: Path, targetFile: Path?): Boolean {
    return copiedFiles.add(CopiedForKey(file, targetFile))
  }

  fun getLibraryFiles(library: JpsLibrary, targetFile: Path?, outputProvider: ModuleOutputProvider): MutableList<Path> {
    val reference = library.createReference()
    val parentReference = reference.parentReference
    val identities = outputProvider.getLibraryJarIdentities(
      libraryName = reference.libraryName,
      moduleLibraryModuleName = if (parentReference is JpsModuleReference) parentReference.moduleName else null,
    )
    // Every identity is marked, not only the first new one, so the check stays a plain loop.
    var contributes = identities.isEmpty()
    for (identity in identities) {
      contributes = copiedIdentities.add(CopiedForKey(identity, targetFile)) || contributes
    }
    if (!contributes) {
      return mutableListOf()
    }

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
private data class CopiedForKey(@JvmField val file: Any, @JvmField val targetFile: Path?)
