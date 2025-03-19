// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.popup.AbstractPopup

open class DocumentationPopupFocusService {
  companion object {
    fun getInstance(project: Project): DocumentationPopupFocusService = project.service()
  }

  open fun focusExistingPopup(currentPopup: AbstractPopup) {
    currentPopup.focusPreferredComponent()
  }
}