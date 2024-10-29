// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ScopeSeparator @ApiStatus.Internal constructor(@Nls val text: String) : ScopeDescriptor(null) {

  override fun getDisplayName(): String {
    return text
  }
}