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
   * Return additional info which should be shown in the "About" dialog. Can be HTML and multiple lines.
   *
   * Note that if you provide HTML here, you should also implement [getExtendedDescription] and provide a plain text version of the content
   * there.
   *
   * @see getExtendedDescription
   */
  fun getDescription(): @DetailedDescription String?

  /**
   * Return additional info which should be only added to the text copied with the action in the "About" dialog. Defaults to the value
   * returned by [getDescription]. Should be plain text, since it's not going to be shown in the UI but only copied to the clipboard.
   */
  fun getExtendedDescription(): @DetailedDescription String? = getDescription()
}
