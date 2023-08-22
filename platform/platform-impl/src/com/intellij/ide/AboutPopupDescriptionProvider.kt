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
   */
  fun getDescription(): @DetailedDescription String?
}
