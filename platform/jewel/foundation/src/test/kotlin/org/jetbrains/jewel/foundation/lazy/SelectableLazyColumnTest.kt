package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

internal class SelectableLazyColumnTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `column with multiple items`() = runBlocking<Unit> {
        val items1 = (0..10).toList()
        val items2 = (11..50).toList()
        val scrollState = SelectableLazyListState(LazyListState())
        composeRule.setContent {
            Box(modifier = Modifier.requiredHeight(100.dp)) {
                SelectableLazyColumn(state = scrollState) {
                    items(
                        items1.size,
                        key = {
                            items1[it]
                        },
                    ) {
                        val itemText = "Item ${items1[it]}"
                        BasicText(itemText, modifier = Modifier.testTag(itemText))
                    }

                    items(
                        items2.size,
                        key = {
                            items2[it]
                        },
                    ) {
                        val itemText = "Item ${items2[it]}"
                        BasicText(itemText, modifier = Modifier.testTag(itemText))
                    }
                }
            }
        }
        composeRule.awaitIdle()
        composeRule.onNodeWithTag("Item 20").assertDoesNotExist()
        scrollState.scrollToItem(20)
        composeRule.onNodeWithTag("Item 20").assertExists()
    }
}
