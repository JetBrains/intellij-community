// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@LcrDslMarker
class LcrTextSpeedSearchParams {

  /**
   * List of ranges that match current speedsearch or null for default behavior
   *
   * See [com.intellij.ui.speedSearch.SpeedSearchSupply.matchingFragments]
   */
  var ranges: Iterable<TextRange>? = null

}
