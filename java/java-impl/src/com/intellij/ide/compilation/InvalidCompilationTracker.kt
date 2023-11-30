// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.compilation

import com.intellij.codeInsight.daemon.impl.MainPassesRunner
import com.intellij.java.JavaBundle
import com.intellij.lang.LanguageUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
internal class InvalidCompilationTracker(
  private val project: Project,
  private val scope: CoroutineScope) {

  private val notificationRef: AtomicReference<Notification?> = AtomicReference(null)

  internal fun analyzeCompilerErrorsInBackground(errorMessages: List<CompilerMessage>) {
    scope.launch(Dispatchers.Default) {
      val scannedFiles = mutableSetOf<VirtualFile>()
      for (message in errorMessages) {
        val file = message.getVirtualFile()
        if (file != null && scannedFiles.add(file) && isFileGreen(project, file)) {
          LOG.warn("Invalid compilation failure in $file")
          InvalidCompilationStatistics.INVALID_COMPILATION_FAILURE.log(project, LanguageUtil.getFileLanguage(file))
          proposeToRebuildModule(project, file)
          break
        }
        if (scannedFiles.size >= MAX_FILES_TO_SCAN) {
          // don't scan too many files to save CPU resources: if there are many errors, and files are also red,
          // then probably it is not an invalid compilation case, and manual actions are needed anyway.
          break
        }
      }
    }
  }

  private fun isFileGreen(project: Project, file: VirtualFile): Boolean {
    var green = false
    ProgressManager.getInstance().executeProcessUnderProgress(Runnable {
      val inspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
      val errors = MainPassesRunner(project, "", inspectionProfile).runMainPasses(listOf(file), HighlightSeverity.ERROR)
        .filterValues { highlightInfos ->
          highlightInfos.any { it.severity == HighlightSeverity.ERROR }
        }
      green = errors.isEmpty()
    }, ProgressIndicatorBase())
    return green
  }

  private suspend fun proposeToRebuildModule(project: Project, file: VirtualFile) {
    val module = readAction {
      FileIndexFacade.getInstance(project).getModuleForFile(file)
    }
    if (module == null) {
      LOG.warn("No module for $file")
      return
    }

    val notification = createNotification(module.name, project)

    val previousNotification = notificationRef.getAndSet(notification)
    if (previousNotification != null) {
      previousNotification.expire()
    }

    notification.notify(project)
  }

  private fun createNotification(moduleName: @NlsSafe String, project: Project): Notification {
    return Notification(NOTIFICATION_GROUP_ID,
                        JavaBundle.message("invalid.compilation.notification.title"),
                        JavaBundle.message("invalid.compilation.notification.content"),
                        NotificationType.WARNING)
      .addAction(NotificationAction.createSimpleExpiring(
        JavaBundle.message("invalid.compilation.notification.action.rebuild", moduleName),
        Runnable {
          val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
          if (module != null) {
            ProjectTaskManager.getInstance(project).rebuild(module)
          }
          else {
            LOG.warn("Module $moduleName not found")
            Messages.showErrorDialog(project,
                                     JavaBundle.message("invalid.compilation.notification.action.rebuild.error.description", moduleName),
                                     JavaBundle.message("invalid.compilation.notification.action.rebuild.error.title", moduleName))
          }
        }
      ))
  }

  fun compilationSucceeded() {
    val previousNotification = notificationRef.getAndSet(null)
    if (previousNotification != null) {
      previousNotification.expire()
    }
  }

  companion object {
    private val LOG = logger<InvalidCompilationTracker>()
    private const val NOTIFICATION_GROUP_ID = "Invalid Compilation Errors"
    private const val MAX_FILES_TO_SCAN = 5

    fun getInstance(project: Project): InvalidCompilationTracker {
      return project.service()
    }
  }
}