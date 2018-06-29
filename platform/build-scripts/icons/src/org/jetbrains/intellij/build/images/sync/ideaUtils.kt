// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.jps.model.serialization.JpsMacroExpander
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.File

private val vcsPattern = """(?<=mapping directory=").*(?=" vcs="Git")""".toRegex()

/**
 * @param project idea project root
 * @return list of VCS roots in [project]
 */
internal fun vcsRoots(project: File): List<File> {
  val vcsXml = project.resolve("${PathMacroUtil.DIRECTORY_STORE_NAME}/vcs.xml")
  return if (vcsXml.exists()) {
    val jme = JpsMacroExpander(emptyMap()).apply {
      addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, project)
    }
    vcsPattern.findAll(vcsXml.readText())
      .map { jme.substitute(it.value, SystemInfo.isFileSystemCaseSensitive) }
      .map {
        val file = File(it)
        if (file.exists()) file else File(project, it)
      }.toList().also {
        log("Found ${it.size} repo roots in $project:")
        it.forEach { log(it.absolutePath) }
      }
  }
  else {
    log("${vcsXml.absolutePath} not found. Using $project")
    listOf(project)
  }
}
