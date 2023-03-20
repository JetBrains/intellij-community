// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.ide.DataManager
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.yield
import java.awt.Component
import java.lang.ref.WeakReference

internal open class DefaultPopupContext(
  private val project: Project,
  private val editor: Editor?,
) : PopupContext {

  override fun preparePopup(builder: ComponentPopupBuilder) {
    builder.setRequestFocus(true)
    builder.setCancelOnClickOutside(true)
  }

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {}

  override fun boundsHandler(): PopupBoundsHandler {
    return DataContextPopupBoundsHandler(::dataContext)
  }

  private var myComponentReference: WeakReference<Component>? = null

  private fun dataContext(): DataContext? {
    EDT.assertIsEdt()
    if (editor is EditorEx && editor.component.isShowing) {
      return editor.dataContext
    }
    val componentReference = myComponentReference
    val component = if (componentReference == null) {
      IdeFocusManager.getInstance(project).focusOwner.also {
        myComponentReference = WeakReference(it)
      }
    }
    else {
      componentReference.get()?.takeIf {
        it.isShowing
      }
    }
    if (component == null) {
      return null
    }
    return DataManager.getInstance().getDataContext(component)
  }
}

internal class DataContextPopupBoundsHandler(
  private val dataContext: () -> DataContext?,
) : PopupBoundsHandler {

  private fun dataContext(popup: AbstractPopup): DataContext {
    return dataContext()
           ?: DataManager.getInstance().getDataContext(popup.component)
  }

  override fun showPopup(popup: AbstractPopup) {
    popup.showInBestPositionFor(dataContext(popup))
  }

  override suspend fun updatePopup(popup: AbstractPopup, resized: Boolean) {
    if (!resized) {
      resizePopup(popup)
      yield()
    }
    popup.setLocation(popup.getBestPositionFor(dataContext(popup)))
  }
}
