// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import com.intellij.platform.icons.modifiers.IconModifier

interface ApplyableIconModifier : IconModifier {
    fun applyTo(layout: LayerLayout): LayerLayout
}
