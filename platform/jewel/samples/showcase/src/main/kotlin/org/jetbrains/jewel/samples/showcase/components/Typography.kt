// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.InfoText
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.typography

private const val SHORT_TEXT = "The quick brown fox jumps over the lazy dog"
private const val LONG_TEXT =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
        "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
        "laboris nisi ut aliquip ex ea commodo consequat."

@Composable
public fun TypographyShowcase(modifier: Modifier = Modifier) {
    Column(modifier) {
        var isLongText by remember { mutableStateOf(false) }
        CheckboxRow("Long text", checked = isLongText, onCheckedChange = { isLongText = it })

        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

        val blurb by remember(isLongText) { mutableStateOf(if (isLongText) LONG_TEXT else SHORT_TEXT) }
        VerticallyScrollableContainer {
            Column(
                modifier = Modifier.padding(vertical = 16.dp).padding(end = scrollbarContentSafePadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val typography = JewelTheme.typography
                TextShowcase(name = "H0", blurb, typography.h0TextStyle)
                TextShowcase(name = "H1", blurb, typography.h1TextStyle)
                TextShowcase(name = "H2", blurb, typography.h2TextStyle)
                TextShowcase(name = "H3", blurb, typography.h3TextStyle)
                TextShowcase(name = "H4", blurb, typography.h4TextStyle)
                TextShowcase(name = "Label", blurb, typography.labelTextStyle)
                TextShowcase(name = "Regular", blurb, typography.regular)
                TextShowcase(name = "Medium", blurb, typography.medium)
                TextShowcase(name = "Small", blurb, typography.small)
                TextShowcase(name = "Editor", blurb, typography.editorTextStyle)
                TextShowcase(name = "Console", blurb, typography.consoleTextStyle)

                Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

                TextShowcase(name = "Info") { InfoText(blurb) }
            }
        }
    }
}

@Composable
private fun TextShowcase(name: String, blurb: String, textStyle: TextStyle, modifier: Modifier = Modifier) {
    TextShowcase(name, modifier) { Text(blurb, style = textStyle) }
}

@Composable
private fun TextShowcase(name: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            name,
            modifier = Modifier.width(64.dp).alignByBaseline(),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.End,
        )
        Box(Modifier.weight(1f).alignByBaseline()) { content() }
    }
}
