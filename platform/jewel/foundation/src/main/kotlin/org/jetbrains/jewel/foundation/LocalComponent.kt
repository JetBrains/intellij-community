// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import javax.swing.JComponent
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the root [JComponent] used to host the current Compose hierarchy.
 *
 * The value is automatically set by:
 * 1. Decorated window on stand alone
 * 2. 'Window' composables from the 'org.jetbrains.jewel.intui.standalone.window'
 * 3. Jewel compose bridge for IDE
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalComponent: ProvidableCompositionLocal<JComponent> = staticCompositionLocalOf {
    error("No Component provided")
}
