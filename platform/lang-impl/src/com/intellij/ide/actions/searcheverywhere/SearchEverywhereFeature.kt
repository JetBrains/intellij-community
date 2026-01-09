// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object SearchEverywhereFeature {
  private const val PLATFORM_KEY = "search.everywhere.new.enabled"
  private const val RIDER_KEY = "search.everywhere.new.rider.enabled"
  private const val CWM_CLIENT_KEY = "search.everywhere.new.cwm.client.enabled"

  private val registryKey: String get() =
    if (isGuest) CWM_CLIENT_KEY
    else if (PlatformUtils.isRider()) RIDER_KEY
    else PLATFORM_KEY

  var isSplit: Boolean
    get() = Registry.`is`(registryKey, true)
    set(value) {
      Registry.get(registryKey).setValue(value)
    }

  private val isGuest: Boolean get() {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return (frontendType is FrontendType.Remote && frontendType.isGuest())
  }

  val allRegistryKeys: List<String>
  @TestOnly get() = listOf(PLATFORM_KEY,
                           RIDER_KEY,
                           CWM_CLIENT_KEY)
}