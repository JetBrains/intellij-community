// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface StartPagePromoter {
  companion object {
    @JvmField
    val START_PAGE_PROMOTER_EP: ExtensionPointName<StartPagePromoter> = ExtensionPointName("com.intellij.startPagePromoter")

    @JvmField
    val PRIORITY_LEVEL_NORMAL: Int = 0

    @JvmField
    val PRIORITY_LEVEL_HIGH: Int = 100
  }

  /**
   * On start page only one random banner with the highest priority is shown
   */
  fun getPriorityLevel(): Int = PRIORITY_LEVEL_NORMAL

  /**
   * @param isEmptyState true if there are no recent projects
   */
  fun canCreatePromo(isEmptyState: Boolean): Boolean = isEmptyState

  fun getPromotion(isEmptyState: Boolean): JComponent
}
