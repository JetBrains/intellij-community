// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.core.JavaPsiBundle.message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.findDirectory
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.impl.file.PsiJavaDirectoryFactory
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.jetbrains.performancePlugin.utils.VcsTestUtil
import io.opentelemetry.context.Context
import org.jetbrains.annotations.Nls

/**
 * Command to add Java file to project
 * Example: %createJavaFile fileName,dstDir,fileType - class, enum, annotation, record, interface
 */
class CreateJavaFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: String = "createJavaFile"
    const val PREFIX: String = CMD_PREFIX + NAME
    val POSSIBLE_FILE_TYPES: Map<String, @Nls String> = mapOf(
      Pair(message("node.class.tooltip").lowercase(), message("node.class.tooltip")),
      Pair(message("node.record.tooltip").lowercase(), message("node.record.tooltip")),
      Pair(message("node.interface.tooltip").lowercase(), message("node.interface.tooltip")),
      Pair(message("node.annotation.tooltip").lowercase(), message("node.annotation.tooltip")),
      Pair(message("node.enum.tooltip").lowercase(), message("node.enum.tooltip"))
    )
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (fileName, filePath, fileType) = extractCommandArgument(PREFIX).replace("\\s", "").split(",")

    val directory = PsiJavaDirectoryFactory
      .getInstance(context.project)
      .createDirectory(
        (context.project.guessProjectDir() ?: throw RuntimeException("Root of the project was not found"))
          .findDirectory(filePath) ?: throw RuntimeException("Can't find file $filePath")
      )

    val templateName = POSSIBLE_FILE_TYPES[fileType.lowercase()]
    if (templateName == null) throw RuntimeException("File type must be one of '${POSSIBLE_FILE_TYPES.keys}'")

    //Disable vcs dialog which appears on adding new file to the project tree
    VcsTestUtil.provisionVcsAddFileConfirmation(context.project, VcsTestUtil.VcsAddFileConfirmation.DO_NOTHING)

    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(Runnable {
      PerformanceTestSpan.TRACER.spanBuilder(NAME).use {
        JavaDirectoryService.getInstance().createClass(directory, fileName, templateName, true)
      }
    }))

  }

  override fun getName(): String = NAME

}