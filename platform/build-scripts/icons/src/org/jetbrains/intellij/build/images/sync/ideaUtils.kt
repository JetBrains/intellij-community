// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsMacroExpander
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val vcsPattern = """(?<=mapping directory=").*(?=" vcs="Git")""".toRegex()
private val jme by lazy {
  JpsMacroExpander(emptyMap()).apply {
    addFileHierarchyReplacements("MAVEN_REPOSITORY", File(System.getProperty("user.home"), ".m2/repository"))
  }
}

/**
 * @param project idea project root
 * @return list of VCS roots in [project]
 */
internal fun vcsRoots(project: Path): List<Path> {
  val vcsXml = project.resolve("${PathMacroUtil.DIRECTORY_STORE_NAME}/vcs.xml")
  if (!Files.exists(vcsXml)) {
    log("${vcsXml.toAbsolutePath()} not found. Using $project")
    return listOf(project)
  }

  jme.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, project.toFile())
  val result = vcsPattern.findAll(Files.readString(vcsXml))
    .map { it.value.takeIf(String::isNotBlank)?.let(::expandJpsMacro) ?: project.toString() }
    .map {
      val file = Paths.get(it)
      if (Files.exists(file)) file else project.resolve(it)
    }
    .filter(Files::exists)
    .toList()
  require(result.isNotEmpty())
  log("Found ${result.size} repo roots in $project:")
  result.forEach { log(it.toAbsolutePath().toString()) }
  return result
}

internal fun expandJpsMacro(text: String) = jme.substitute(text, SystemInfo.isFileSystemCaseSensitive)

internal fun searchTestRoots(project: String): Set<Path> {
  return try {
    jpsProject(project)
      .modules.flatMap {
        it.getSourceRoots(JavaSourceRootType.TEST_SOURCE) +
        it.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
      }.mapTo(mutableSetOf()) { it.file.toPath() }
  }
  catch (e: IOException) {
    System.err.println(e.message)
    emptySet()
  }
}

internal fun jpsProject(path: String): JpsProject {
  val file = Paths.get(FileUtil.toCanonicalPath(path))
  return if (path.endsWith(".ipr")
             || Files.isDirectory(file.resolve(PathMacroUtil.DIRECTORY_STORE_NAME))
             || Files.isDirectory(file) && file.endsWith(PathMacroUtil.DIRECTORY_STORE_NAME)) {
    JpsSerializationManager.getInstance().loadModel(path, null).project
  }
  else {
    jpsProject(file.parent.toString())
  }
}