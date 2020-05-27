// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.*
import com.intellij.ui.JBColor
import com.intellij.ui.layout.*
import com.intellij.ui.popup.PopupFactoryImpl
import java.util.concurrent.atomic.AtomicReference

class InplaceMethodExtractor(val project: Project, val editor: Editor) : InplaceRefactoring(editor, null, project) {

  private val popupPanel by lazy {
    panel {
      row { this.checkBox("Make static & pass fields") }
      row { checkBox("Make constructor") }
      row { checkBox("Annotate") }
      row {
        link("Go to declaration", null) {}
        comment("Ctrl+N")
      }
      row {
        link("More options", null) { }
        comment("Ctrl+Alt+M")
      }
    }
  }

  private val inlayReference = AtomicReference<Inlay<PresentationRenderer>>()

  override fun getInlayPresentation(): SelectableInlayPresentation? {
    val editor = myEditor as? EditorImpl ?: return null
    val factory = PresentationFactory(editor)

    val roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6)
    val padding = InlayPresentationFactory.Padding(4, 4, 4, 4)

    val inactiveIcon = factory.container(
      presentation =  factory.icon(AllIcons.Actions.InlayGear),
      padding = padding,
      roundedCorners = roundedCorners,
      background = JBColor.LIGHT_GRAY
    )
    val activeIcon = factory.container(
      presentation =  factory.icon(AllIcons.Actions.InlayGear),
      padding = padding,
      roundedCorners = roundedCorners,
      background = JBColor.DARK_GRAY
    )
    val inactivePadded = factory.container(inactiveIcon, padding = InlayPresentationFactory.Padding(3, 6, 0, 0))
    val activePadded = factory.container(activeIcon, padding = InlayPresentationFactory.Padding(3, 6, 0, 0))

    return SelectableInlayButton(editor, inactivePadded, activePadded, activePadded, inlayReference)
  }

  override fun inlayOnSelection(place: VisualPosition, presentation: SelectableInlayPresentation) {
    editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, place)
    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(popupPanel, null)
      .setRequestFocus(true)
      .addListener(object: JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) { presentation.isSelected = false }
      })
      .createPopup()
    popup.showInBestPositionFor(editor)
  }

  override fun createInlay(): Inlay<PresentationRenderer>? {
    val inlay = super.createInlay()
    inlayReference.set(inlay)
    return inlay
  }

  override fun performRefactoring(): Boolean {
    return false
  }

  override fun collectAdditionalElementsToRename(stringUsages: MutableList<Pair<PsiElement, TextRange>>) {
  }

  override fun shouldSelectAll(): Boolean = false

  override fun getCommandName(): String = "TODO"

}