// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface KotlinUIDslRendererComponent {

  /**
   * Separator that was configured by the renderer (can be hidden or used for other rows while filtering in ComboBox-es with
   * `ComboBox.isSwingPopup = false`)
   */
  val listSeparator: ListSeparator?

  /**
   * Returns the text used for copy operations (Ctrl+C) in lists and ComboBox-es.
   * Never returns null, as for multi-selection the number of lines in the copied text
   * must match the number of selected items.
   */
  fun getCopyText(): @NlsSafe String
}