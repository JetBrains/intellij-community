package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule

open class BasicJewelUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Suppress("ImplicitUnitReturnType")
    protected fun runComposeTest(composable: @Composable () -> Unit, block: suspend ComposeContentTestRule.() -> Unit) =
        runBlocking {
            composeRule.setContent(composable)
            composeRule.block()
        }
}
