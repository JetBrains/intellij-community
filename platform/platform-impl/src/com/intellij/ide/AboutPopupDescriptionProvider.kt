// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.util.NlsContexts.DetailedDescription

/**
 * Allows plugins to add additional details to display in *Help | About* popup.
 * 
 * Register in `com.intellij.aboutInfoProvider` extension point.
 */
interface AboutPopupDescriptionProvider {
  /**
   * Return additional info which should be shown in About popup.
   */
  fun getDescription(): @DetailedDescription String?
}