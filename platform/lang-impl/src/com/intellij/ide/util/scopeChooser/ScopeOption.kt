// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class ScopeOption {
  LIBRARIES,
  SEARCH_RESULTS,
  FROM_SELECTION,
  USAGE_VIEW,
  EMPTY_SCOPES
}