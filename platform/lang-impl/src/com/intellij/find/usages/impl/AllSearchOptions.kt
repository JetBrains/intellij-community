// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.UsageOptions
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class AllSearchOptions(
  val options: UsageOptions,
  val textSearch: Boolean?,
)
