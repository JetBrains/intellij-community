// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
public class JewelThemeTest {
    @Test
    public fun `isIslands defaults to false outside a theme`() {
        runComposeUiTest {
            var observed: Boolean? = null
            setContent { observed = JewelTheme.isIslands }
            assertEquals(false, observed)
        }
    }
}
