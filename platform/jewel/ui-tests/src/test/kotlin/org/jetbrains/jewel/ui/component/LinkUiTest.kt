package org.jetbrains.jewel.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class LinkUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `should apply provided modifier correctly`() {
        rule.setContent { IntUiTheme { Link("Whatever", {}, modifier = Modifier.testTag("123banana")) } }
        rule.onNodeWithText("Whatever").assertExists()
        rule.onNodeWithTag("123banana").assertExists()
    }
}
