// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.util.NlsContexts.DetailedDescription

/**
 * Allows plugins to add additional details to display in *Help | About* popup.
 * 
 * Register in `com.intellij.aboutPopupDescriptionProvider` extension point.
 */
interface AboutPopupDescriptionProvider {
  /**
   * Return additional info which should be shown in the "About" dialog.
   * Plain text only. Only one line supported.
   */
  fun getDescription(): @DetailedDescription String?

  /**
   * Return additional info which should be only added to the text copied with the action in the "About" dialog.
   * Plain text only, and defaults to the value returned by [getDescription].
   */
  fun getExtendedDescription(): @DetailedDescription String? = getDescription()
}
