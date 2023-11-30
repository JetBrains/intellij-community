// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.picker.ColorListener
import com.intellij.ui.picker.ColorPickerPopupCloseListener
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component

/**
 * @author Konstantin Bulenkov
 */
@Service(Service.Level.APP)
class ColorChooserService {
  @JvmOverloads
  fun showDialog(parent: Component,
                 caption: @NlsContexts.DialogTitle String?,
                 preselectedColor: Color?,
                 enableOpacity: Boolean = false,
                 listeners: List<ColorPickerListener> = emptyList(),
                 opacityInPercent: Boolean = false): Color? {
    return showDialog(project = null, parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent)
  }

  @JvmOverloads
  fun showDialog(project: Project?,
                 parent: Component,
                 caption: @NlsContexts.DialogTitle String?,
                 preselectedColor: Color?,
                 enableOpacity: Boolean = false,
                 listeners: List<ColorPickerListener> = emptyList(),
                 opacityInPercent: Boolean = false): Color? {
    val clientInstance = ClientColorChooserService.getCurrentInstance()
    return clientInstance.showDialog(project, parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent)
  }

  @JvmOverloads
  fun showPopup(project: Project?,
                currentColor: Color?,
                editor: Editor?,
                listener: ColorListener,
                showAlpha: Boolean = false,
                showAlphaAsPercent: Boolean = false,
                popupCloseListener: ColorPickerPopupCloseListener? = null) {
    // dispatching based on editor? (each client has their own editor copy)
    val clientInstance = ClientColorChooserService.getCurrentInstance()
    clientInstance.showPopup(project, currentColor, editor, listener, showAlpha, showAlphaAsPercent, popupCloseListener)
  }

  @JvmOverloads
  fun showPopup(project: Project?,
                currentColor: Color?,
                listener: ColorListener,
                location: RelativePoint? = null,
                showAlpha: Boolean = false,
                showAlphaAsPercent: Boolean = false,
                popupCloseListener: ColorPickerPopupCloseListener? = null) {
    val clientInstance = ClientColorChooserService.getCurrentInstance()
    clientInstance.showPopup(project, currentColor, listener, location, showAlpha, showAlphaAsPercent, popupCloseListener)
  }

  @Deprecated("This method doesn't support remote development.",
              ReplaceWith("showPopup(project, currentColor, listener, location, showAlpha)"))
  fun showColorPickerPopup(project: Project?,
                           currentColor: Color?,
                           listener: ColorListener,
                           location: RelativePoint?,
                           showAlpha: Boolean) {
    showPopup(project, currentColor, listener, location, showAlpha, false, null)
  }

  companion object {
    @JvmStatic
    val instance: ColorChooserService
      get() = ApplicationManager.getApplication().getService(ColorChooserService::class.java)
  }
}

/**
 * A client specific version of [ColorChooserService]. In remote development, this component should pass the UI to the frontend
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface ClientColorChooserService {
  companion object {
    @JvmStatic
    fun getCurrentInstance(): ClientColorChooserService {
      return ApplicationManager.getApplication().getService(ClientColorChooserService::class.java)
    }
  }

  fun showDialog(project: Project?,
                 parent: Component,
                 @NlsContexts.DialogTitle caption: String?,
                 preselectedColor: Color?,
                 enableOpacity: Boolean,
                 listeners: List<ColorPickerListener>,
                 opacityInPercent: Boolean): Color?

  fun showPopup(project: Project?,
                currentColor: Color?,
                listener: ColorListener,
                location: RelativePoint?,
                showAlpha: Boolean,
                showAlphaAsPercent: Boolean,
                popupCloseListener: ColorPickerPopupCloseListener?)

  fun showPopup(project: Project?,
                currentColor: Color?,
                editor: Editor?,
                listener: ColorListener,
                showAlpha: Boolean,
                showAlphaAsPercent: Boolean,
                popupCloseListener: ColorPickerPopupCloseListener?)
}
