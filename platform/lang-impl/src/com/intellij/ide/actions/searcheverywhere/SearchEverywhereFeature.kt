// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object SearchEverywhereFeature {
  private const val PLATFORM_KEY = "search.everywhere.new.enabled"
  private const val PLATFORM_INTERNAL_KEY = "search.everywhere.new.internal.enabled"

  private const val RIDER_KEY = "search.everywhere.new.rider.enabled"
  private const val CLION_KEY = "search.everywhere.new.clion.enabled"
  private const val CWM_CLIENT_KEY = "search.everywhere.new.cwm.client.enabled"

  private val platformKey: String get() =
    if (ApplicationManager.getApplication().isInternal) PLATFORM_INTERNAL_KEY
    else PLATFORM_KEY

  private val platformBasedProductKey: String? get() =
    if (PlatformUtils.isRider()) RIDER_KEY
    else if (PlatformUtils.isCLion()) CLION_KEY
    else if (isGuest) CWM_CLIENT_KEY
    else null

  var isSplit: Boolean
    get() =
      Registry.`is`(platformKey, false) &&
      (platformBasedProductKey?.let {
        Registry.`is`(it, false)
      } ?: true)

    set(value) {
      Registry.get(platformKey).setValue(value)
      platformBasedProductKey?.let {
        Registry.get(it).setValue(value)
      }
    }

  private val isGuest: Boolean get() {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return (frontendType is FrontendType.Remote && frontendType.isGuest())
  }

  val allRegistryKeys: List<String>
  @TestOnly get() = listOf(PLATFORM_KEY,
                           PLATFORM_INTERNAL_KEY,
                           RIDER_KEY,
                           CLION_KEY,
                           CWM_CLIENT_KEY)
}