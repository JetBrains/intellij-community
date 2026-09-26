// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Allows customizing only the advertiser area of the stock lookup bottom panel.
 *
 * Returning `null` keeps the default advertiser component for the lookup instance.
 * Returning a component replaces only the advertiser area while the stock spinner,
 * hint button, menu, and layout remain platform-owned.
 */
@ApiStatus.Internal
interface LookupBottomPanelAdvertiserCustomizer {
  fun createAdvertiserComponent(lookup: Lookup, advertiserComponent: JComponent): JComponent?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<LookupBottomPanelAdvertiserCustomizer> =
      ExtensionPointName("com.intellij.lookup.bottomPanelAdvertiserCustomizer")

    @JvmStatic
    fun getAdvertiserComponent(lookup: Lookup, advertiserComponent: JComponent): JComponent? {
      return EP_NAME.computeSafeIfAny { it.createAdvertiserComponent(lookup, advertiserComponent) }
    }
  }
}
