// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JPanel

interface StartPagePromoter {
  companion object {
    @JvmField
    val START_PAGE_PROMOTER_EP = ExtensionPointName<StartPagePromoter>("com.intellij.startPagePromoter")
  }

  fun getPromotionForInitialState(): JPanel?

  fun needToHideSingleProject(path: String): Boolean = false
}
