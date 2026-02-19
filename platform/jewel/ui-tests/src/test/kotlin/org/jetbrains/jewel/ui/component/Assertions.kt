package org.jetbrains.jewel.ui.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

fun SemanticsNodeInteraction.assertEditableTextEquals(expected: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))

fun SemanticsNodeInteraction.assertEditableTextEquals(expected: AnnotatedString): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, expected))

fun SemanticsNodeInteraction.assertCursorAtPosition(index: Int): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(index)))
