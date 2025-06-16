// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.ide.actions.SearchEverywhereSplitIncompatible
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object SearchEverywhereFeature {
  val isSplit: Boolean get() = Registry.`is`(registryKey, false) && SearchEverywhereSplitIncompatible.EP_NAME.extensionList.isEmpty()

  private val isCwmClient: Boolean get() {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return (frontendType is FrontendType.RemoteDev && !frontendType.isLuxSupported)
  }

  val registryKey: String get() =
    if (PlatformUtils.isRider()) "search.everywhere.new.rider.enabled"
    else if (isCwmClient) "search.everywhere.new.cwm.client.enabled"
    else "search.everywhere.new.enabled"

  @get:TestOnly
  val additionalRegistryToTurnOffSplitInTests: Map<String, String>
    get() = mapOf("search.everywhere.new.enabled" to "false",
                  "search.everywhere.new.rider.enabled" to "false",
                  "search.everywhere.new.cwm.client.enabled" to "false")
}