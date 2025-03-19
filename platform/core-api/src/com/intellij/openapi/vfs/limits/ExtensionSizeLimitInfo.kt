// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.limits

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ExtensionSizeLimitInfo(
  val content: Int? = null,
  val intellijSense: Int? = null,
  val preview: Int? = null,
)