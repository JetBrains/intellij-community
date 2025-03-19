// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlayTags {
  const val LIST_TAG: Byte = 0
  const val TEXT_TAG: Byte = 1
  const val ICON_TAG: Byte = 2
  const val COLLAPSE_BUTTON_TAG: Byte = 3
  const val COLLAPSIBLE_LIST_EXPANDED_BRANCH_TAG: Byte = 4
  const val COLLAPSIBLE_LIST_COLLAPSED_BRANCH_TAG: Byte = 5
  const val COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG: Byte = 6
  const val COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG: Byte = 7
  const val COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG: Byte = 8
  const val COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG: Byte = 9
  const val CLICK_HANDLER_SCOPE_TAG: Byte = 10
}