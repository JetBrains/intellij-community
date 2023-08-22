// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.ui.popup.AbstractPopup

internal sealed interface PopupContext {

  fun preparePopup(builder: ComponentPopupBuilder)

  fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI)

  fun boundsHandler(): PopupBoundsHandler
}
