// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class DeclarativeInlayActionService {
  open fun invokeInlayMenu(hintData: InlayData, e: EditorMouseEvent, relativePoint: RelativePoint) {
    val project = e.editor.project ?: return
    val document = e.editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    val providerId = hintData.providerId
    val providerInfo = InlayHintsProviderFactory.getProviderInfo(psiFile.language, providerId) ?: return
    val providerName = providerInfo.providerName

    val inlayMenu: AnAction = ActionManager.getInstance().getAction("InlayMenu")
    val inlayMenuActionGroup = inlayMenu as ActionGroup
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_FILE, psiFile)
      .add(CommonDataKeys.EDITOR, e.editor)
      .add(InlayHintsProvider.PROVIDER_ID, providerId)
      .add(InlayHintsProvider.PROVIDER_NAME, providerName)
      .add(InlayHintsProvider.INLAY_PAYLOADS, hintData.payloads?.associate { it.payloadName to it.payload })
      .build()

    val popupMenu = JBPopupFactory.getInstance().createActionGroupPopup(null, inlayMenuActionGroup, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
    popupMenu.show(relativePoint)
  }

  fun invokeActionHandler(actionData: InlayActionData, e: EditorMouseEvent) {
    val handlerId = actionData.handlerId
    val handler = InlayActionHandler.getActionHandler(handlerId)
    if (handler != null) {
      logActionHandlerInvoked(handlerId, handler.javaClass)
      handler.handleClick(e, actionData.payload)
    }
  }

  open fun logActionHandlerInvoked(handlerId: String, handlerClass: Class<out InlayActionHandler>) {
    InlayActionHandlerUsagesCollector.clickHandled(handlerId, handlerClass)
  }

  // TODO see if showTooltip/hideTooltip can be extracted to here
}