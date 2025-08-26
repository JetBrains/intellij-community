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
  val isSplit: Boolean get() = Registry.`is`(registryKey, false)

  private val isGuest: Boolean get() {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return (frontendType is FrontendType.Remote && frontendType.isGuest())
  }

  val registryKey: String get() =
    if (PlatformUtils.isRider()) "search.everywhere.new.rider.enabled"
    else if (isGuest) "search.everywhere.new.cwm.client.enabled"
    else "search.everywhere.new.enabled"

  // Enable the first Search Everywhere implementation (`com.intellij.ide.actions.searcheverywhere`).
  @get:TestOnly
  val additionalRegistryToTurnOffSplitSEInTests: Map<String, String>
    get() = mapOf("search.everywhere.new.enabled" to "false",
                  "search.everywhere.new.rider.enabled" to "false",
                  "search.everywhere.new.cwm.client.enabled" to "false")

  // Enable the new Search Everywhere implementation (`com.intellij.platform.searchEverywhere`).
  @get:TestOnly
  val additionalRegistryToTurnOnSplitSEInTests: Map<String, String>
    get() = mapOf("search.everywhere.new.enabled" to "true",
                  "search.everywhere.new.rider.enabled" to "true",
                  "search.everywhere.new.cwm.client.enabled" to "true")
}