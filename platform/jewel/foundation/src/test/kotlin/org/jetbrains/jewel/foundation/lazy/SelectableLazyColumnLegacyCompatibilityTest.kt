// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@Suppress("ImplicitUnitReturnType")
internal class SelectableLazyColumnLegacyCompatibilityTest {
    @get:Rule val composeRule = createComposeRule()

    private val items = listOf("A", "B", "C")

    @Test
    fun `legacy mismatch SelectionMode Single vs SelectionMode Multiple applies Single on interaction`() = runTest {
        lateinit var state: SelectableLazyListState
        val callbackInvocations = mutableListOf<List<Int>>()
        composeRule.setContent {
            state =
                rememberSelectableLazyListState(
                    selectionMode = SelectionMode.Multiple,
                    initialSelectedKeys = listOf("A", "B"),
                )
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                SelectableLazyColumn(
                    modifier = Modifier.testTag("list"),
                    selectionMode = SelectionMode.Single,
                    state = state,
                    onSelectedIndexesChange = { callbackInvocations += it },
                ) {
                    items(items.size, key = { items[it] }) { index ->
                        BasicText(items[index], modifier = Modifier.testTag(items[index]))
                    }
                }
            }
        }

        composeRule.awaitIdle()
        assertEquals(setOf("A", "B"), state.selectedKeys)
        assertEquals(emptyList<List<Int>>(), callbackInvocations)

        composeRule.onNodeWithTag("C", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()

        assertEquals(setOf("C"), state.selectedKeys)
        assertEquals(listOf(listOf(2)), callbackInvocations)
    }

    @Test
    fun `legacy mismatch SelectionMode None vs SelectionMode Single suppresses new click selection`() = runTest {
        lateinit var state: SelectableLazyListState
        val callbackInvocations = mutableListOf<List<Int>>()
        composeRule.setContent {
            state =
                rememberSelectableLazyListState(selectionMode = SelectionMode.Single, initialSelectedKeys = listOf("A"))
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                SelectableLazyColumn(
                    modifier = Modifier.testTag("list"),
                    selectionMode = SelectionMode.None,
                    state = state,
                    onSelectedIndexesChange = { callbackInvocations += it },
                ) {
                    items(items.size, key = { items[it] }) { index ->
                        BasicText(items[index], modifier = Modifier.testTag(items[index]))
                    }
                }
            }
        }

        composeRule.awaitIdle()
        assertEquals(setOf("A"), state.selectedKeys)
        assertEquals(emptyList<List<Int>>(), callbackInvocations)

        composeRule.onNodeWithTag("B", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()

        assertEquals(setOf("A"), state.selectedKeys)
        assertEquals(emptyList<List<Int>>(), callbackInvocations)
    }
}
