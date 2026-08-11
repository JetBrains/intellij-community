// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

@ApiStatus.NonExtendable
interface CollapsibleRow : Row {

  /**
   * Expanded state of the row
   */
  var expanded: Boolean

  /**
   * Packs the window's height on expand and collapse
   */
  var packWindowHeight: Boolean

  fun setTitle(@NlsContexts.BorderTitle title: String)

  fun setTitleFont(font: Font)

  fun addExpandedListener(action: (Boolean) -> Unit)

}