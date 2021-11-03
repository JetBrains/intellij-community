// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

internal open class DefaultPopupContext(
  private val project: Project,
  private val editor: Editor?,
) : PopupContext {

  override fun preparePopup(builder: ComponentPopupBuilder) {
    builder.setRequestFocus(true)
    builder.setCancelOnClickOutside(true)
  }

  private fun dataContext(): DataContext {
    EDT.assertIsEdt()
    if (editor is EditorEx && editor.component.isShowing) {
      return editor.dataContext
    }
    else {
      val referenceComponent = IdeFocusManager.getInstance(project).focusOwner
      return DataManager.getInstance().getDataContext(referenceComponent)
    }
  }

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    val resized = popupUI.useStoredSize()
    popupUI.updatePopup {
      if (!resized.get()) {
        resizePopup(popup)
        yield()
      }
      popup.setLocation(popup.getBestPositionFor(dataContext()))
    }
  }

  override fun showPopup(popup: AbstractPopup) {
    popup.showInBestPositionFor(dataContext())
  }
}
