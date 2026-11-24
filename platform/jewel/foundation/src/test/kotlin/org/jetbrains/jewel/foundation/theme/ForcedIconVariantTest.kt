// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How [OverrideDarkMode] sets [LocalForcedIconVariant]. An icon that follows the IDE theme keeps following it when the
 * dark state stays the same. Another dark state, or a theme that forces a variant, forces the variant.
 */
@OptIn(ExperimentalTestApi::class)
public class ForcedIconVariantTest {
    @Test
    public fun `the same dark state keeps an icon that follows the IDE theme`() {
        for (isDark in listOf(false, true)) {
            assertEquals(null, observe(isDark = isDark, forced = null) { OverrideDarkMode(isDark, it) })
        }
    }

    @Test
    public fun `another dark state forces the variant`() {
        assertEquals(ForcedIconVariant.Dark, observe(isDark = false, forced = null) { OverrideDarkMode(true, it) })
        assertEquals(ForcedIconVariant.Light, observe(isDark = true, forced = null) { OverrideDarkMode(false, it) })
    }

    @Test
    public fun `a forced variant stays forced`() {
        assertEquals(
            ForcedIconVariant.Light,
            observe(isDark = false, forced = ForcedIconVariant.Light) { OverrideDarkMode(false, it) },
        )
        assertEquals(
            ForcedIconVariant.Dark,
            observe(isDark = false, forced = ForcedIconVariant.Light) { OverrideDarkMode(true, it) },
        )
    }

    @Test
    public fun `outside a theme the variant is forced`() {
        runComposeUiTest {
            var observed: ForcedIconVariant? = null
            setContent { OverrideDarkMode(true) { observed = LocalForcedIconVariant.current } }
            assertEquals(ForcedIconVariant.Dark, observed)
        }
    }

    /** The [LocalForcedIconVariant] inside [override], in a theme that is dark if [isDark] and forces [forced]. */
    private fun observe(
        isDark: Boolean,
        forced: ForcedIconVariant?,
        override: @Composable (@Composable () -> Unit) -> Unit,
    ): ForcedIconVariant? {
        var observed: ForcedIconVariant? = ForcedIconVariant.Light
        var read = false
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalIsDarkTheme provides isDark, LocalForcedIconVariant provides forced) {
                    override {
                        observed = LocalForcedIconVariant.current
                        read = true
                    }
                }
            }
        }
        assertEquals(true, read)
        return observed
    }
}
