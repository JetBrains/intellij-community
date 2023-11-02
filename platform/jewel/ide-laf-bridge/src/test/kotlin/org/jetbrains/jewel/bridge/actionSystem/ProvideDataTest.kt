package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProvideDataTest {

    @JvmField @Rule
    val rule = createComposeRule()

    @Test
    fun `one component`() {
        runBlocking {
            val rootDataProviderModifier = RootDataProviderModifier()
            var focusManager: FocusManager? = null
            rule.setContent {
                focusManager = LocalFocusManager.current
                Box(
                    modifier = rootDataProviderModifier.testTag("provider")
                        .provideData {
                            when (it) {
                                "data" -> "ok"
                                else -> null
                            }
                        }
                        .focusable(),
                )
            }
            rule.awaitIdle()
            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()

            rule.onNodeWithTag("provider").assertIsFocused()

            assertEquals("ok", rootDataProviderModifier.dataProvider("data"))
            assertEquals(null, rootDataProviderModifier.dataProvider("another_data"))
        }
    }

    @Test
    fun `component hierarchy`() {
        runBlocking {
            val rootDataProviderModifier = RootDataProviderModifier()
            var focusManager: FocusManager? = null
            rule.setContent {
                focusManager = LocalFocusManager.current
                Box(
                    modifier = rootDataProviderModifier.testTag("root_provider")
                        .provideData {
                            when (it) {
                                "isRoot" -> "yes"
                                else -> null
                            }
                        }
                        .focusable(),
                ) {
                    Box(modifier = Modifier.testTag("non_data_provider").focusable()) {
                        Box(
                            modifier =
                            Modifier.testTag("data_provider_item")
                                .provideData {
                                    when (it) {
                                        "data" -> "ok"
                                        else -> null
                                    }
                                }
                                .focusable(),
                        )
                    }
                }
            }

            rule.awaitIdle()
            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()

            rule.onNodeWithTag("root_provider").assertIsFocused()
            assertEquals("yes", rootDataProviderModifier.dataProvider("isRoot"))
            assertEquals(null, rootDataProviderModifier.dataProvider("data"))

            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()

            rule.onNodeWithTag("non_data_provider").assertIsFocused()
            // non_data_provider still should provide isRoot == true because it should be taken from root
            // but shouldn't provide "data" yet
            assertEquals("yes", rootDataProviderModifier.dataProvider("isRoot"))
            assertEquals(null, rootDataProviderModifier.dataProvider("data"))

            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()

            rule.onNodeWithTag("data_provider_item").assertIsFocused()

            assertEquals("yes", rootDataProviderModifier.dataProvider("isRoot"))
            assertEquals("ok", rootDataProviderModifier.dataProvider("data"))
        }
    }
}
