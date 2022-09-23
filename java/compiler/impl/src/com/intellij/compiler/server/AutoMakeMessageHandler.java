// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.ProblemsView
import com.intellij.compiler.impl.CompileDriver.Companion.convertToCategory
import com.intellij.notification.Notification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.problems.WolfTheProblemSolver
import org.jetbrains.jps.api.CmdlineRemoteProto
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.BuilderMessage
import org.jetbrains.jps.api.GlobalOptions
import java.awt.EventQueue
import java.util.*

private val LAST_AUTO_MAKE_NOTIFICATION = Key.create<Notification>("LAST_AUTO_MAKE_NOTIFICATION")

/**
 * @author Eugene Zhuravlev
 */
internal class AutoMakeMessageHandler(private val project: Project) : DefaultMessageHandler(project) {
  private var buildStatus: BuilderMessage.BuildEvent.Status = BuilderMessage.BuildEvent.Status.SUCCESS
  private val wolf: WolfTheProblemSolver = WolfTheProblemSolver.getInstance(project)

  @Volatile
  private var isUnprocessedFsChangesDetected = false
  private val context = AutomakeCompileContext(project)

  private val problemView by lazy(LazyThreadSafetyMode.NONE) { ProblemsView.getInstance(project) }

  init {
    context.progressIndicator.start()
  }

  fun unprocessedFSChangesDetected() = isUnprocessedFsChangesDetected

  override fun handleBuildEvent(sessionId: UUID, event: BuilderMessage.BuildEvent) {
    if (project.isDisposed) {
      return
    }

    when (event.eventType) {
      BuilderMessage.BuildEvent.Type.BUILD_COMPLETED -> {
        context.progressIndicator.stop()
        if (event.hasCompletionStatus()) {
          val status = event.completionStatus
          buildStatus = status
          if (status == BuilderMessage.BuildEvent.Status.CANCELED) {
            context.progressIndicator.cancel()
          }
        }

        val errors = context.getMessageCount(CompilerMessageCategory.ERROR)
        val warnings = context.getMessageCount(CompilerMessageCategory.WARNING)
        EventQueue.invokeLater {
          if (project.isDisposed) {
            return@invokeLater
          }
          val publisher = project.messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS)
          publisher.automakeCompilationFinished(errors, warnings, context)
        }
      }
      BuilderMessage.BuildEvent.Type.FILES_GENERATED -> {
        val publisher = project.messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS)
        for (generatedFile in event.generatedFilesList) {
          val root = FileUtil.toSystemIndependentName(generatedFile.outputRoot)
          val relativePath = FileUtil.toSystemIndependentName(generatedFile.relativePath)
          publisher.fileGenerated(root, relativePath)
        }
      }
      BuilderMessage.BuildEvent.Type.CUSTOM_BUILDER_MESSAGE -> {
        if (event.hasCustomBuilderMessage()) {
          val message = event.customBuilderMessage
          if (GlobalOptions.JPS_SYSTEM_BUILDER_ID == message.builderId && GlobalOptions.JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID == message.messageType) {
            isUnprocessedFsChangesDetected = true
          }
        }
      }
      else -> {}
    }
  }

  override fun handleCompileMessage(sessionId: UUID, message: BuilderMessage.CompileMessage) {
    if (project.isDisposed) {
      return
    }

    val kind = message.kind
    if (kind == BuilderMessage.CompileMessage.Kind.PROGRESS) {
      if (message.hasDone()) {
        problemView.setProgress(message.text, message.done)
      }
      else {
        problemView.setProgress(message.text)
      }
    }
    else {
      val category = convertToCategory(kind) ?: return
      // only process supported kinds of messages
      val sourceFilePath = if (message.hasSourceFilePath()) message.sourceFilePath else null
      val url = if (sourceFilePath == null) {
        null
      }
      else {
        VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(sourceFilePath))
      }

      val line = if (message.hasLine()) message.line else -1
      val column = if (message.hasColumn()) message.column else -1
      val compilerMessage = context
        .createAndAddMessage(category, message.text, url, line.toInt(), column.toInt(), null, message.moduleNamesList)
      if (category === CompilerMessageCategory.ERROR || kind == BuilderMessage.CompileMessage.Kind.JPS_INFO) {
        if (category === CompilerMessageCategory.ERROR) {
          informWolf(message)
        }
        if (compilerMessage != null) {
          problemView.addMessage(compilerMessage, sessionId)
        }
      }
    }
  }

  override fun handleFailure(sessionId: UUID, failure: CmdlineRemoteProto.Message.Failure) {
    if (project.isDisposed) {
      return
    }

    val description = (if (failure.hasDescription()) failure.description else null)
                      ?: if (failure.hasStacktrace()) failure.stacktrace else ""
    val message = JavaCompilerBundle.message("notification.compiler.auto.build.failure", description)
    CompilerManager.NOTIFICATION_GROUP.createNotification(message, MessageType.INFO).notify(project)
    problemView.addMessage(CompilerMessageImpl(project, CompilerMessageCategory.ERROR, message), sessionId)
  }

  override fun sessionTerminated(sessionId: UUID) {
    var statusMessage: String? = null /*"Auto make completed"*/
    when (buildStatus) {
      BuilderMessage.BuildEvent.Status.SUCCESS -> {}
      BuilderMessage.BuildEvent.Status.UP_TO_DATE -> {}
      BuilderMessage.BuildEvent.Status.ERRORS -> statusMessage = JavaCompilerBundle.message(
        "notification.compiler.auto.build.completed.with.errors")
      BuilderMessage.BuildEvent.Status.CANCELED -> {}
    }
    if (statusMessage != null) {
      val notification = CompilerManager.NOTIFICATION_GROUP.createNotification(statusMessage, MessageType.INFO)
      if (!project.isDisposed) {
        notification.notify(project)
      }
      project.putUserData(LAST_AUTO_MAKE_NOTIFICATION, notification)
    }
    else {
      val notification = project.getUserData(LAST_AUTO_MAKE_NOTIFICATION)
      if (notification != null) {
        notification.expire()
        project.putUserData(LAST_AUTO_MAKE_NOTIFICATION, null)
      }
    }
    if (!project.isDisposed) {
      ProblemsView.getInstanceIfCreated(project)?.let { view ->
        view.clearProgress()
        view.clearOldMessages(null, sessionId)
      }
    }
  }

  override fun getProgressIndicator() = context.progressIndicator

  private fun informWolf(message: BuilderMessage.CompileMessage) {
    val srcPath = message.sourceFilePath ?: return
    if (project.isDisposed) {
      return
    }

    ApplicationManager.getApplication().runReadAction {
      if (project.isDisposed) {
        return@runReadAction
      }

      val vFile = LocalFileSystem.getInstance().findFileByPath(srcPath) ?: return@runReadAction
      val line = message.line.toInt()
      val column = message.column.toInt()
      if (line > 0 && column > 0) {
        val problem = wolf.convertToProblem(vFile, line, column, arrayOf(message.text))
        wolf.weHaveGotProblems(vFile, listOf(problem))
      }
      else {
        wolf.queue(vFile)
      }
    }
  }
}