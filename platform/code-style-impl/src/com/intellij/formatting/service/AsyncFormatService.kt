// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeFormattingData
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class AsyncFormattingService {

  companion object {
    @JvmStatic
    fun getInstance() = ApplicationManager.getApplication().service<AsyncFormattingService>()
  }

  open fun asyncFormatElement(service: FormattingService, element: PsiElement, range: TextRange, canChangeWhitespaceOnly: Boolean) {
    val file = element.containingFile
    val project = file.project
    ReadAction
      .nonBlocking<CodeFormattingData> { CodeFormattingData.prepare(file, listOf(range)) }
      .withDocumentsCommitted(project)
      .expireWhen { project.isDisposed || !file.isValid }
      .finishOnUiThread(ModalityState.nonModal()) { _ ->
        CommandProcessor.getInstance().runUndoTransparentAction {
          WriteAction.run<RuntimeException> {
            service.formatElement(element, range, canChangeWhitespaceOnly)
          }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}