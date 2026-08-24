/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui.components

import org.jetbrains.annotations.ApiStatus
import javax.swing.AbstractButton
import javax.swing.Action
import javax.swing.plaf.ButtonUI

abstract class OptionButtonUI : ButtonUI() {
  abstract fun showPopup(toSelect: Action? = null, ensureSelection: Boolean = true)
  abstract fun closePopup()
  abstract fun togglePopup()

  /**
   * The child button this UI laid [half] out as, or `null` when the half is absent or this UI has none.
   *
   * An option button delegates [SplitButtonZones] here because the halves are the UI's own children: it owns
   * both their bounds and the listeners that fire from them.
   */
  @ApiStatus.Internal
  open fun splitButtonHalfButton(half: SplitButtonHalf): AbstractButton? = null
}