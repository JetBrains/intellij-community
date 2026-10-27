// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.filters

import com.intellij.platform.icons.filters.ColorFilter
import kotlinx.serialization.Serializable

/** See [com.intellij.platform.icons.filters.gray] for the meaning of the values. */
@Serializable
class GrayColorFilter(val brightness: Int, val contrast: Int, val alpha: Int) : ColorFilter
