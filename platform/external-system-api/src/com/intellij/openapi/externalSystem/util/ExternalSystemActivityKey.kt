// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.platform.backend.observation.ActivityKey
import org.jetbrains.annotations.Nls

object ExternalSystemActivityKey : ActivityKey {
  override val presentableName: @Nls String
    get() = "external-system"
}