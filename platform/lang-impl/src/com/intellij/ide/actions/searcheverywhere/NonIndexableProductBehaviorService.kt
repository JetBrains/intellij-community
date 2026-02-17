// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NonIndexableProductBehaviorService {

  companion object {
    @JvmStatic
    fun getInstance(): NonIndexableProductBehaviorService {
      return service()
    }
  }

  fun shouldLookupInProjectScopes(): Boolean
}

internal class DefaultNonIndexableProductBehaviorService : NonIndexableProductBehaviorService {
  override fun shouldLookupInProjectScopes(): Boolean {
    return true
  }
}
