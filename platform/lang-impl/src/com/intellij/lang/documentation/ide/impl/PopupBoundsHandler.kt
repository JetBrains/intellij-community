// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.ui.popup.AbstractPopup

interface PopupBoundsHandler {

  fun showPopup(popup: AbstractPopup)

  suspend fun updatePopup(popup: AbstractPopup, resized: Boolean)
}
