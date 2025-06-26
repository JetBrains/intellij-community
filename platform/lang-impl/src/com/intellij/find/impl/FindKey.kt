// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FindKey {
  @JvmStatic
  val isCwmClient: Boolean get() {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return (frontendType is FrontendType.Remote && frontendType.isGuest())
  }

  val isEnabled: Boolean
    @JvmStatic
    get() {
      if (isCwmClient) return Registry.`is`("find.in.files.split.cwm")
      return Registry.`is`("find.in.files.split")
    }
}