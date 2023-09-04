// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.tree

import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import java.awt.Component
import javax.swing.JComponent

internal class SpeedSearchActionHandler(private val speedSearch: SpeedSearchBase<*>) {

  fun isSpeedSearchActive(): Boolean = speedSearch.isPopupActive

  fun activateSpeedSearch() {
    if (speedSearch.isAvailable && !isSpeedSearchActive()) {
      speedSearch.showPopup()
    }
  }

}

internal fun Component.getSpeedSearchActionHandler(): SpeedSearchActionHandler? {
  val contextComponent = (this as? JComponent?) ?: return null
  val speedSearch = (SpeedSearchSupply.getSupply(contextComponent, true) as? SpeedSearchBase<*>?) ?: return null
  if (!speedSearch.isAvailable) return null
  return SpeedSearchActionHandler(speedSearch)
}
