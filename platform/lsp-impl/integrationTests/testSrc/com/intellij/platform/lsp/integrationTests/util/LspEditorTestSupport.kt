// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.util

import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Gives the focus to the editor and puts the caret at [offset]. */
internal fun JEditorUiComponent.putCaretAt(offset: Int) {
  click()
  moveCaretToOffset(offset)
}

/** Types [text] at [offset], as a user does: each character is its own document change. */
internal fun JEditorUiComponent.typeAt(offset: Int, text: String) {
  putCaretAt(offset)
  type(text)
}

/** Selects the text between the offsets and types [text] over it. */
internal fun JEditorUiComponent.typeOver(startOffset: Int, endOffset: Int, text: String) {
  click()
  setSelection(startOffset, endOffset)
  type(text)
}

/**
 * Types [text] at the caret, with a shorter delay between the characters than the default of the Driver SDK.
 * A line break in [text] is the Enter key, which is not the same as an inserted `\n`: the editor indents the new line.
 */
private fun JEditorUiComponent.type(text: String) {
  keyboard {
    text.split('\n').forEachIndexed { index, line ->
      if (index > 0) enter()
      if (line.isNotEmpty()) typeText(line, delayBetweenCharsInMs = 20)
    }
  }
}

/**
 * The `waitFor` of the Driver SDK with a getter and a checker, without a result.
 * As the last statement of an `ideFrame { }` block, the SDK function gets `Unit` for its value type from the block.
 */
internal fun <T> waitForValue(message: String, timeout: Duration = 30.seconds, getter: () -> T, checker: (T) -> Boolean) {
  waitFor(message, timeout, getter = getter, checker = checker)
}
