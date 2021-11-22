// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.ui.popup.AbstractPopup

internal sealed interface PopupContext {

  fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI)

  fun showPopup(popup: AbstractPopup)
}
