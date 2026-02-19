// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// helper in modern language to use modern API to provide not ineffective and ancient but non-blocking implementation
internal class DaemonCodeAnalyzerRepaintIconHelper(coroutineScope: CoroutineScope) {
  @Suppress("UsagesOfObsoleteApi")
  private val alarm = Alarm(
    threadToUse = Alarm.ThreadToUse.SWING_THREAD,
    parentDisposable = null,
    activationComponent = null,
    coroutineScope = coroutineScope,
  )

  fun repaintTrafficIcon(file: PsiFile, editor: Editor, progress: Double) {
    if (!ApplicationManager.getApplication().isCommandLine && (progress >= 1 || alarm.isEmpty)) {
      scheduleRepaintErrorStripeAndIcon(editor = editor, file = file, project = file.project, delay = 50L)
    }
  }

  fun scheduleRepaintErrorStripeAndIcon(editor: Editor, project: Project, file: PsiFile?, delay: Long) {
    val modalityState = ModalityState.defaultModalityState()
    alarm.schedule {
      delay(delay)
      if (editor.isDisposed) {
        return@schedule
      }

      val markup = editor.getMarkupModel()
      if (markup !is EditorMarkupModelImpl) {
        return@schedule
      }

      withContext(modalityState.asContextElement()) {
        withContext(Dispatchers.EDT) {
          if (!editor.isDisposed) {
            markup.repaintTrafficLightIcon()
          }
        }

        if (!editor.isDisposed) {
          project.serviceAsync<ErrorStripeUpdateManager>().asyncRepaintErrorStripePanel(markup, file)
        }
      }
    }
  }
}