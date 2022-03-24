// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupFactoryImpl
import java.awt.Point

internal class InlinePopupContext(
  project: Project,
  private val editor: Editor,
  private val point: Point
) : DefaultPopupContext(project, editor) {

  override fun showPopup(popup: AbstractPopup) {
    editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, point)
    Disposer.register(popup) {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, null)
    }
    super.showPopup(popup)
  }
}
