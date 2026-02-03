// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JComponent

@Experimental
internal val LocalComponent = staticCompositionLocalOf<JComponent> {
  error("CompositionLocal LocalComponent not provided")
}