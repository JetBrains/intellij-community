// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.picker.ColorListener
import com.intellij.ui.picker.ColorPickerPopupCloseListener
import java.awt.Color
import java.awt.Component

/**
 * @author Konstantin Bulenkov
 */
abstract class ColorChooserService {
  @JvmOverloads
  open fun showDialog(parent: Component,
                      caption: @NlsContexts.DialogTitle String?,
                      preselectedColor: Color?,
                      enableOpacity: Boolean = false,
                      listeners: List<ColorPickerListener> = emptyList(),
                      opacityInPercent: Boolean = false): Color? {
    throw UnsupportedOperationException()
  }

  @JvmOverloads
  open fun showDialog(project: Project?,
                      parent: Component,
                      caption: @NlsContexts.DialogTitle String?,
                      preselectedColor: Color?,
                      enableOpacity: Boolean = false,
                      listeners: List<ColorPickerListener> = emptyList(),
                      opacityInPercent: Boolean = false): Color? {
    throw UnsupportedOperationException()
  }

  @JvmOverloads
  open fun showPopup(project: Project?,
                     currentColor: Color?,
                     editor: Editor?,
                     listener: ColorListener,
                     showAlpha: Boolean = false,
                     showAlphaAsPercent: Boolean = false,
                     popupCloseListener: ColorPickerPopupCloseListener? = null) {
    throw UnsupportedOperationException()
  }

  @JvmOverloads
  open fun showPopup(project: Project?,
                     currentColor: Color?,
                     listener: ColorListener,
                     location: RelativePoint? = null,
                     showAlpha: Boolean = false,
                     showAlphaAsPercent: Boolean = false,
                     popupCloseListener: ColorPickerPopupCloseListener? = null) {
    throw UnsupportedOperationException()
  }

  @Deprecated("This method doesn't support remote development.",
              ReplaceWith("showPopup(project, currentColor, listener, location, showAlpha)"))
  open fun showColorPickerPopup(project: Project?,
                                currentColor: Color?,
                                listener: ColorListener,
                                location: RelativePoint?,
                                showAlpha: Boolean) {
    throw UnsupportedOperationException()
  }

  companion object {
    @JvmStatic
    val instance: ColorChooserService
      get() = ApplicationManager.getApplication().getService(ColorChooserService::class.java)
  }
}
