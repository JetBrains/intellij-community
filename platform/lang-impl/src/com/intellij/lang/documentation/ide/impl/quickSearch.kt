// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.util.gotoByName.QuickSearchComponent
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.ui.ComponentUtil
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.HintUpdateSupply
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.util.ui.EDT
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import java.awt.Component
import javax.swing.JComponent

internal fun quickSearchPopupContext(project: Project): PopupContext? {
  val focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project)
                         ?: return null
  return quickSearchPopupContext(project, focusedComponent)
         ?: hintUpdateSupplyPopupContext(project, focusedComponent)
}

private fun quickSearchPopupContext(project: Project, focusedComponent: Component): PopupContext? {
  val quickSearchComponent = ComponentUtil.getParentOfType(QuickSearchComponent::class.java, focusedComponent)
                             ?: return null
  return QuickSearchPopupContext(project, quickSearchComponent)
}

private fun hintUpdateSupplyPopupContext(project: Project, focusedComponent: Component): PopupContext? {
  val hintUpdateSupply = HintUpdateSupply.getSupply(focusedComponent as JComponent)
                         ?: return null
  return HintUpdateSupplyPopupContext(project, focusedComponent, hintUpdateSupply)
}

private abstract class UpdatingPopupContext(
  private val project: Project,
) : SecondaryPopupContext() {

  private val items = MutableSharedFlow<Any?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  final override fun requestFlow(): Flow<DocumentationRequest?> = items.asRequestFlow()

  final override fun preparePopup(builder: ComponentPopupBuilder) {
    super.preparePopup(builder)
    builder.addUserData(object : PopupUpdateProcessor(project) {
      override fun updatePopup(lookupItemObject: Any?) {
        items.tryEmit(lookupItemObject)
      }
    })
  }
}

private class QuickSearchPopupContext(
  project: Project,
  private val searchComponent: QuickSearchComponent,
) : UpdatingPopupContext(project) {

  // otherwise, selecting SE items by mouse would close the popup
  override val closeOnClickOutside: Boolean get() = false

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    super.setUpPopup(popup, popupUI)
    searchComponent.registerHint(popup)
    Disposer.register(popup) {
      searchComponent.unregisterHint()
    }
  }

  override fun baseBoundsHandler(): PopupBoundsHandler {
    return AdjusterPopupBoundsHandler(searchComponent as Component)
  }
}

private class HintUpdateSupplyPopupContext(
  project: Project,
  private val referenceComponent: Component,
  private val hintUpdateSupply: HintUpdateSupply,
) : UpdatingPopupContext(project) {

  override val closeOnClickOutside: Boolean get() = true

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    super.setUpPopup(popup, popupUI)
    hintUpdateSupply.registerHint(popup)
  }

  override fun baseBoundsHandler(): PopupBoundsHandler {
    return DataContextPopupBoundsHandler {
      DataManager.getInstance().getDataContext(referenceComponent)
    }
  }
}

private fun Flow<Any?>.asRequestFlow(): Flow<DocumentationRequest?> {
  EDT.assertIsEdt()
  return map {
    readAction {
      val targetElement = PSIPresentationBgRendererWrapper.toPsi(it) ?: return@readAction null
      if (!targetElement.isValid) {
        return@readAction null
      }
      PsiElementDocumentationTarget(targetElement.project, targetElement).documentationRequest()
    }
  }
}
