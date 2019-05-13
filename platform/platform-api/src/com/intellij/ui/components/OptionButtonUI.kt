/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui.components

import javax.swing.Action
import javax.swing.plaf.ButtonUI

abstract class OptionButtonUI : ButtonUI() {
  abstract fun showPopup(toSelect: Action? = null, ensureSelection: Boolean = true)
  abstract fun closePopup()
  abstract fun togglePopup()
}