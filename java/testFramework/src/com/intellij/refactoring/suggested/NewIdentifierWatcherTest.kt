// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class NewIdentifierWatcherTest : LightJavaCodeInsightFixtureTestCase() {
  fun test1() {
    doTest(
      """
                class A {
                    void foo() {
                        <caret>
                    }
                }
            """.trimIndent(),
      listOf("int", "abc")) {
      myFixture.type("int abc = 1;")
    }
  }

  fun testPasteCode() {
    doTest(
      """
                class A {
                    void foo() {
                        <caret>
                    }
                }
            """.trimIndent(),
      emptyList()) {
      myFixture.type("int abc = ")
      executeCommand {
        runWriteAction {
          myFixture.editor.document.insertString(myFixture.editor.caretModel.offset, "X * Y * Z")
        }
      }
    }
  }

  private fun doTest(
    initialFileText: String,
    expectedNewIdentifiers: List<String>,
    editingActions: () -> Unit
  ) {
    myFixture.configureByText(JavaFileType.INSTANCE, initialFileText)

    val watcher = NewIdentifierWatcher(3)
    val listener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        watcher.documentChanged(event, JavaLanguage.INSTANCE)
      }
    }
    myFixture.editor.document.addDocumentListener(listener)

    editingActions()

    myFixture.editor.document.removeDocumentListener(listener)

    val identifiers = watcher.lastNewIdentifierRanges().map { myFixture.editor.document.getText(it) }
    assertEquals(expectedNewIdentifiers, identifiers)
  }
}