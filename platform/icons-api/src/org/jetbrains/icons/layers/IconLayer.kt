// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.layers

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.modifiers.IconModifier

/**
 * To serialize IconLayer, serializersModule from IconManager might be used.
 */
@ExperimentalIconsApi
interface IconLayer {
  val modifier: IconModifier
}