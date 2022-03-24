// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.util.gotoByName.QuickSearchComponent
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.ComponentUtil
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.util.ui.EDT
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import java.awt.Component

internal fun quickSearchComponent(project: Project): QuickSearchComponent? {
  val focusedComponent: Component? = WindowManagerEx.getInstanceEx().getFocusedComponent(project)
  return ComponentUtil.getParentOfType(QuickSearchComponent::class.java, focusedComponent)
}

internal class QuickSearchPopupContext(
  private val project: Project,
  private val searchComponent: QuickSearchComponent,
) : SecondaryPopupContext() {

  private val items = MutableSharedFlow<Any?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override val referenceComponent: Component get() = searchComponent as Component

  override fun preparePopup(builder: ComponentPopupBuilder) {
    super.preparePopup(builder)
    builder.addUserData(object : PopupUpdateProcessor(project) {
      override fun updatePopup(lookupItemObject: Any?) {
        items.tryEmit(lookupItemObject)
      }
    })
  }

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    super.setUpPopup(popup, popupUI)
    searchComponent.registerHint(popup)
    Disposer.register(popup) {
      searchComponent.unregisterHint()
    }
  }

  override fun requestFlow(): Flow<DocumentationRequest?> = items.asRequestFlow()
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
