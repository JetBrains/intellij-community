package com.intellij.lang.documentation.ide.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.popup.AbstractPopup

open class DocumentationPopupFocusService {
  companion object {
    @JvmStatic
    fun instance(project: Project): DocumentationPopupFocusService = project.service()
  }

  open fun focusExistingPopup(currentPopup: AbstractPopup) {
    currentPopup.focusPreferredComponent()
  }
}