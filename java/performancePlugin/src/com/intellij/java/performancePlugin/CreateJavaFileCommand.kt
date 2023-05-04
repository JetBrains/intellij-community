// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.core.JavaPsiBundle.message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.impl.file.PsiJavaDirectoryFactory
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter

class CreateJavaFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "createJavaFile"
    const val PREFIX = CMD_PREFIX + NAME
    val POSSIBLE_FILE_TYPES = mapOf(
      Pair(message("node.class.tooltip").lowercase(), message("node.class.tooltip")),
      Pair(message("node.record.tooltip").lowercase(), message("node.record.tooltip")),
      Pair(message("node.interface.tooltip").lowercase(), message("node.interface.tooltip")),
      Pair(message("node.annotation.tooltip").lowercase(), message("node.annotation.tooltip")),
      Pair(message("node.enum.tooltip").lowercase(), message("node.enum.tooltip"))
    )
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (fileName, filePath, fileType) = extractCommandArgument(PREFIX).split(",")

    val directory = PsiJavaDirectoryFactory
      .getInstance(context.project)
      .createDirectory(context.project.guessProjectDir()!!.findFileOrDirectory(filePath)!!)

    val templateName = POSSIBLE_FILE_TYPES.getOrElse(fileType.lowercase()) { return@getOrElse null }
    if (templateName == null)  throw RuntimeException("File type must be one of ${POSSIBLE_FILE_TYPES.keys}")

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().getService(JavaDirectoryService::class.java)
        .createClass(directory, fileName, templateName, true)
    }
  }

  override fun getName(): String {
    return NAME
  }

}