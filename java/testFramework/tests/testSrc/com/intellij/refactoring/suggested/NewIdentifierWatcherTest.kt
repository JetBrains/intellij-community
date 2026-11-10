// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class NewIdentifierWatcherTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun test1(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun testPasteCode(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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
      withContext(Dispatchers.EDT) {
        executeCommand {
          runWriteAction {
            myFixture.editor.document.insertString(myFixture.editor.caretModel.offset, "X * Y * Z")
          }
        }
      }
    }
  }

  private suspend fun doTest(
    initialFileText: String,
    expectedNewIdentifiers: List<String>,
    editingActions: suspend () -> Unit
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
