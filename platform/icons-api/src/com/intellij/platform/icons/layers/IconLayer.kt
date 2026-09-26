// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.layers

import com.intellij.platform.icons.modifiers.IconModifier

/** To serialize IconLayer, serializersModule from IconManager might be used. */
interface IconLayer {
    val modifier: IconModifier
}
