// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

class DeclarativeInlayRenderer(
  @TestOnly
  val presentationList: InlayPresentationList,
  private val fontMetricsStorage: InlayTextMetricsStorage,
  val providerId: String,
) : EditorCustomElementRenderer {
  private var inlay: Inlay<DeclarativeInlayRenderer>? = null

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return presentationList.getWidthInPixels(fontMetricsStorage)
  }

  @RequiresEdt
  fun updateState(newState: TinyTree<Any?>, disabled: Boolean, hasBackground: Boolean) {
    presentationList.updateState(newState, disabled, hasBackground)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    presentationList.paint(inlay, g, targetRegion, textAttributes)
  }

  fun handleLeftClick(e: EditorMouseEvent, pointInsideInlay: Point, controlDown: Boolean) {
    presentationList.handleClick(e, pointInsideInlay, fontMetricsStorage, controlDown)
  }

  fun handleRightClick(e: EditorMouseEvent) {
    val project = e.editor.project ?: return
    val document = e.editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    val providerInfo = InlayHintsProviderFactory.getProviderInfo(psiFile.language, providerId) ?: return
    val providerName = providerInfo.providerName
    JBPopupMenu.showByEvent(e.mouseEvent, "InlayMenu", DefaultActionGroup(DisableDeclarativeInlayAction(providerName, providerId)))
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
}

private class DisableDeclarativeInlayAction(private val providerName: @Nls String, private val providerId: String) : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = CodeInsightBundle.message("inlay.hints.declarative.disable.action.text", providerName)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val settings = DeclarativeInlayHintsSettings.getInstance(project)
    settings.setProviderEnabled(providerId, false)
  }
}