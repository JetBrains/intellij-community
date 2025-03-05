package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.actionSystem.provideData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

public class ProvideDataTest {
    @JvmField @Rule
    public val rule: ComposeContentTestRule = createComposeRule()

    @Test
    public fun `one component`() {
        runBlocking {
            val sink = TestDataSink()
            val rootDataProviderModifier = RootDataProviderModifier()
            var focusManager: FocusManager? = null
            rule.setContent {
                focusManager = LocalFocusManager.current
                Box(
                    modifier =
                        rootDataProviderModifier.testTag("provider").provideData { set("data", "ok") }.focusable()
                )
            }
            rule.awaitIdle()
            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()

            rule.onNodeWithTag("provider").assertIsFocused()
            rootDataProviderModifier.uiDataSnapshot(sink)

            assertEquals("ok", sink.get("data"))
            assertNull(sink.get("another_data"))
        }
    }

    @Test
    public fun `component hierarchy`() {
        runBlocking {
            val rootDataProviderModifier = RootDataProviderModifier()
            var focusManager: FocusManager? = null
            rule.setContent {
                focusManager = LocalFocusManager.current
                Box(
                    modifier =
                        rootDataProviderModifier
                            .testTag("root_provider")
                            .provideData {
                                set("is_root", "yes")
                                set("data", "notOk")
                            }
                            .focusable()
                ) {
                    Box(modifier = Modifier.testTag("non_data_provider").focusable()) {
                        Box(
                            modifier =
                                Modifier.testTag("data_provider_item")
                                    .provideData {
                                        set("data", "ok")
                                        set("one", "1")
                                    }
                                    .focusable()
                        )
                    }
                }
            }

            rule.awaitIdle()
            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()

            val sink = TestDataSink()
            rule.onNodeWithTag("root_provider").assertIsFocused()
            rootDataProviderModifier.uiDataSnapshot(sink)
            assertEquals("yes", sink.get("is_root"))
            assertEquals("notOk", sink.get("data"))
            assertNull(sink.get("one"))

            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()
            sink.clear()

            rule.onNodeWithTag("non_data_provider").assertIsFocused()
            rootDataProviderModifier.uiDataSnapshot(sink)
            // non_data_provider still should provide isRoot == true because it should be taken from
            // root
            // but shouldn't provide "one" yet
            assertEquals("yes", sink.get("is_root"))
            assertEquals("notOk", sink.get("data"))
            assertNull(sink.get("one"))

            focusManager!!.moveFocus(FocusDirection.Next)
            rule.awaitIdle()
            sink.clear()

            rule.onNodeWithTag("data_provider_item").assertIsFocused()
            rootDataProviderModifier.uiDataSnapshot(sink)
            assertEquals("yes", sink.get("is_root"))
            assertEquals("ok", sink.get("data"))
            assertEquals("1", sink.get("one"))
        }
    }
}
