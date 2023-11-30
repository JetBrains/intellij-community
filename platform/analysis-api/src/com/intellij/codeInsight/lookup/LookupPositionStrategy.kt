// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class LookupPositionStrategy {
  /** The popup will be shown below the caret if there is enough space, otherwise it will be shown above */
  PREFER_BELOW,

  /** The popup will be shown only above the caret.
   *  If there is not enough space above, the lookup height will be reduced to the actual space.
   */
  ONLY_ABOVE
}