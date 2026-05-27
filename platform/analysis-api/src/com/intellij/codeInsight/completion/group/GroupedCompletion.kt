// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.group

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GroupedCompletion {
  /**
   * Use [GroupedCompletionContributor.isGroupEnabledInApp] instead
   *
   * Determines whether the grouped code completion feature is enabled at the application level.
   * @return true if grouped code completion is enabled at the application level, otherwise false.
   */
  fun isEnabled(): Boolean
}
