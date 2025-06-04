// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SearchEverywhereFeature {
  val isSplit: Boolean get() = Registry.`is`(registryKey, false)

  val registryKey: String get() =
    if (PlatformUtils.isRider()) "search.everywhere.new.rider.enabled"
    else "search.everywhere.new.enabled"
}