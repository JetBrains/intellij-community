// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object SearchEverywhereFeature {
  private const val PLATFORM_KEY = "search.everywhere.new.enabled"

  var isSplit: Boolean
    get() =
      // Consider the registry key value only for internal mode users and UI tests
      !ApplicationManager.getApplication().isInternal && !ApplicationManagerEx.isInIntegrationTest() || Registry.`is`(PLATFORM_KEY, false)

    set(value) {
      Registry.get(PLATFORM_KEY).setValue(value)
    }

  val allRegistryKeys: List<String> @TestOnly get() = listOf(PLATFORM_KEY)
}