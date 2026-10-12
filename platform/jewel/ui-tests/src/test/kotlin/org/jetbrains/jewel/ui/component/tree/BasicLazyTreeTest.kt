// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.tree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.lazy.tree.BasicLazyTree
import org.jetbrains.jewel.foundation.lazy.tree.DefaultMacOsTreeColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.tree.DefaultTreeViewKeyActions
import org.jetbrains.jewel.foundation.lazy.tree.DefaultTreeViewOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

internal class BasicLazyTreeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `cmd right-click on unselected row replaces selection`() {
        rule.setContent { TestTree() }

        rule.onNodeWithText("root 1").performMouseInput { click() }.assertIsSelected()

        rule.onNodeWithText("root 3").performMultiModalInput {
            key { keyDown(Key.MetaLeft) }
            mouse { rightClick() }
            key { keyUp(Key.MetaLeft) }
        }

        rule.onNodeWithText("root 1").assertIsNotSelected()
        rule.onNodeWithText("root 3").assertIsSelected()
    }

    @Test
    fun `cmd right-click on a selected row keeps the multi-selection`() {
        rule.setContent { TestTree() }

        rule.onNodeWithText("root 1").performMouseInput { click() }

        rule
            .onNodeWithText("root 2") // adds root 2 to the selection
            .performMultiModalInput {
                key { keyDown(Key.MetaLeft) }
                mouse { click() }
                key { keyUp(Key.MetaLeft) }
            }

        rule
            .onNodeWithText("root 2") // cmd + right click root 2
            .performMultiModalInput {
                key { keyDown(Key.MetaLeft) }
                mouse { rightClick() }
                key { keyUp(Key.MetaLeft) }
            }

        rule.onNodeWithText("root 1").assertIsSelected()
        rule.onNodeWithText("root 2").assertIsSelected()
    }

    @Test
    fun `shift right-click on another item should not select a range of items`() {
        rule.setContent { TestTree() }

        rule.onNodeWithText("root 1").performMouseInput { click() }

        rule.onNodeWithText("root 3").performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }
            mouse { rightClick() }
            key { keyUp(Key.ShiftLeft) }
        }

        rule.onNodeWithText("root 1").assertIsNotSelected()
        rule.onNodeWithText("root 2").assertIsNotSelected()
        rule.onNodeWithText("root 3").assertIsSelected()
    }

    @Test
    fun `right-click on selected item should keep it selected`() {
        rule.setContent { TestTree() }

        rule.onNodeWithText("root 1").performMouseInput { click() }

        rule
            .onNodeWithText("root 2") // adds root 2 to the selection
            .performMultiModalInput {
                key { keyDown(Key.MetaLeft) }
                mouse { click() }
                key { keyUp(Key.MetaLeft) }
            }

        rule.onNodeWithText("root 1").performMouseInput { rightClick() }

        rule.onNodeWithText("root 1").assertIsSelected()
        rule.onNodeWithText("root 2").assertIsSelected()
    }

    @Test
    fun `right-clicking an unselected item makes it selected`() {
        rule.setContent { TestTree() }

        rule.onNodeWithText("root 1").performMouseInput { click() }

        rule.onNodeWithText("root 3").performMouseInput { rightClick() }

        rule.onNodeWithText("root 1").assertIsNotSelected()
        rule.onNodeWithText("root 3").assertIsSelected()
    }

    @Test
    fun `cmd left-click unselected item should add item to selection`() {
        rule.setContent { TestTree() }

        rule.onNodeWithText("root 1").performMouseInput { click() }

        rule.onNodeWithText("root 3").performMultiModalInput {
            key { keyDown(Key.MetaLeft) }
            mouse { click() }
            key { keyUp(Key.MetaLeft) }
        }

        rule.onNodeWithText("root 1").assertIsSelected()
        rule.onNodeWithText("root 3").assertIsSelected()
    }

    @Composable
    private fun TestTree() {
        val treeContent = buildTree {
            addNode("root 1") {
                addLeaf("leaf 1")
                addLeaf("leaf 2")
            }
            addNode("root 2") {
                addLeaf("leaf 1")
                addNode("node 1") {
                    addLeaf("leaf 1")
                    addLeaf("leaf 2")
                }
            }
            addNode("root 3") {
                addLeaf("leaf 1")
                addLeaf("leaf 2")
            }
        }

        IntUiTheme {
            val treeState = rememberTreeState()

            BasicLazyTree(
                treeContent,
                Color.Red,
                Color.Yellow,
                Color.Blue,
                5.dp,
                CornerSize(10.dp),
                PaddingValues(5.dp),
                PaddingValues(5.dp),
                10.dp,
                3.dp,
                {},
                {},
                {},
                {},
                treeState = treeState,
                keyActions =
                    DefaultTreeViewKeyActions(
                        DefaultMacOsTreeColumnKeybindings,
                        DefaultTreeViewOnKeyEvent(DefaultMacOsTreeColumnKeybindings, treeState),
                    ),
                nodeContent = { element -> Box(Modifier.fillMaxWidth()) { Text(element.data, Modifier.padding(2.dp)) } },
            )
        }
    }
}
