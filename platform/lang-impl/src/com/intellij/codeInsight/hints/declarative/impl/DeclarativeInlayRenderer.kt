// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

@ApiStatus.Internal
class DeclarativeInlayRenderer(
  @TestOnly
  val presentationList: InlayPresentationList,
  private val fontMetricsStorage: InlayTextMetricsStorage,
  val providerId: String,
  private val position: InlayPosition,
) : EditorCustomElementRenderer {
  private var inlay: Inlay<DeclarativeInlayRenderer>? = null

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return presentationList.getWidthInPixels(fontMetricsStorage).fullWidth
  }

  @RequiresEdt
  fun updateState(newState: TinyTree<Any?>, disabled: Boolean, hintFormat: HintFormat) {
    presentationList.updateState(newState, disabled, hintFormat)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    presentationList.paint(inlay, g, targetRegion, textAttributes)
  }

  fun handleLeftClick(e: EditorMouseEvent, pointInsideInlay: Point, controlDown: Boolean) {
    presentationList.handleClick(e, pointInsideInlay, fontMetricsStorage, controlDown)
  }

  fun handleHover(e: EditorMouseEvent): LightweightHint? {
    return presentationList.handleHover(e)
  }

  fun handleRightClick(e: EditorMouseEvent) {
    val project = e.editor.project ?: return
    val document = e.editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    val providerInfo = InlayHintsProviderFactory.getProviderInfo(psiFile.language, providerId) ?: return
    val providerName = providerInfo.providerName

    val inlayMenu: AnAction = ActionManager.getInstance().getAction("InlayMenu")
    val inlayMenuActionGroup = inlayMenu as ActionGroup
    val popupMenu = ActionManager.getInstance().createActionPopupMenu("InlayMenuPopup", inlayMenuActionGroup)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_FILE, psiFile)
      .add(CommonDataKeys.EDITOR, e.editor)
      .add(InlayHintsProvider.PROVIDER_ID, providerId)
      .add(InlayHintsProvider.PROVIDER_NAME, providerName)
      .add(InlayHintsProvider.INLAY_PAYLOADS, presentationList.payloads)
      .build()
    popupMenu.setDataContext {
      dataContext
    }

    JBPopupMenu.showByEvent(e.mouseEvent, popupMenu.component)
  }

  fun setInlay(inlay: Inlay<DeclarativeInlayRenderer>) {
    this.inlay = inlay
  }

  fun getMouseArea(pointInsideInlay: Point): InlayMouseArea? {
    return presentationList.getMouseArea(pointInsideInlay, fontMetricsStorage)
  }

  // this should not be shown anywhere, but it is required to show custom menu in com.intellij.openapi.editor.impl.EditorImpl.DefaultPopupHandler.getActionGroup
  override fun getContextMenuGroupId(inlay: Inlay<*>): String {
    return "DummyActionGroup"
  }

  internal fun toInlayData(): InlayData {
    val inlay = this.inlay!! // null cannot be here because this method is called only on the renderer got from the inlay instance
    val pos = when (position) {
      // important to store position based on the inlay offset, not the renderer one
      // the latter does not receive updates from the inlay model when the document is changed
      is InlineInlayPosition -> InlineInlayPosition(inlay.offset, position.relatedToPrevious, position.priority)
      is EndOfLinePosition -> EndOfLinePosition(inlay.editor.document.getLineNumber(inlay.offset))
    }
    return presentationList.toInlayData(pos, providerId)
  }

  internal fun getSourceId(): String = presentationList.sourceId
}
