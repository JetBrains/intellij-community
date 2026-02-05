// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.layers

import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import javax.swing.Icon
import kotlinx.serialization.Serializable

@Serializable class SwingIconLayer(val legacyIcon: Icon, override val modifier: IconModifier) : IconLayer
