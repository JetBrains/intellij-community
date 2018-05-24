// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.impl.GuiTestCase
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import java.io.File
import java.util.regex.Pattern

/**
 * Returns list of short names of jar files located under the project folder
 * @param projectPath full path to the project folder
 * @return List<String>
 * */
fun getKotlinLibInProject(projectPath: String) =
    FileUtil.findFilesOrDirsByMask(
        Pattern.compile("\\S*\\.jar"),
        File(projectPath))
        .map { it.name }

/**
 * Converts explicit path separators into current OS separators
 * */
internal fun String.normalizeSeparator() = replace("\\", File.separator).replace("/", File.separator)

internal fun GuiTestCase.waitUntil(condition: () -> Boolean) {
  Pause.pause(object : Condition("Wait until condition is done") {
    override fun test() = condition()
  })
}

fun createFolder(projectPath: String, folder: String): File {
  val folderFile = File("$projectPath${if (!projectPath.endsWith(File.separator)) File.separator else ""}$folder".normalizeSeparator())
  folderFile.mkdirs()
  return folderFile
}

// TODO: TestData related function. Uncomment and remake when TestData is supported ok
//fun copySampleFiles(projectStructure: ProjectStructure, projectPath: String) {
//  val dstSourceFolder = createFolder(projectPath, sourceRoots[projectStructure]!!.sourceRoot)
//  val dstTestFolder = createFolder(projectPath, sourceRoots[projectStructure]!!.testRoot)
//  sampleMainKt.copyTo(File(dstSourceFolder, sampleMainKt.name))
//  sampleTestKt.copyTo(File(dstTestFolder, sampleTestKt.name))
//}

