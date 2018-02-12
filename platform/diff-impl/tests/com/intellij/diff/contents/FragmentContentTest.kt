/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.contents

import com.intellij.diff.HeavyDiffTestCase
import com.intellij.diff.actions.DocumentFragmentContent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange

class FragmentContentTest : HeavyDiffTestCase() {
  private lateinit var document: Document
  private lateinit var fragment: Document

  private lateinit var documentContent: DocumentContent
  private lateinit var fragmentContent: DocumentFragmentContent

  override fun setUp() {
    super.setUp()

    document = EditorFactory.getInstance().createDocument("0123456789")
    documentContent = DocumentContentImpl(document)
    fragmentContent = DocumentFragmentContent(null, documentContent, TextRange(3, 7))
    fragment = fragmentContent.document

    fragmentContent.onAssigned(true)
  }

  override fun tearDown() {
    try {
      fragmentContent.onAssigned(false)
    }
    finally {
      super.tearDown()
    }
  }

  fun testSynchronization() {
    assertEquals("3456", fragment.text)
    replaceString(fragment, 1, 3, "xy")
    assertEquals("0123xy6789", document.text)
    replaceString(document, 4, 6, "45")
    assertEquals("0123456789", document.text)
    assertEquals("3456", fragment.text)
    replaceString(document, 0, 1, "xyz")
    assertEquals("3456", fragment.text)
    replaceString(fragment, 1, 3, "xy")
    assertEquals("xyz123xy6789", document.text)
  }

  fun testEditReadonlyDocument() {
    documentContent.onAssigned(false)
    document.setReadOnly(true)

    documentContent.onAssigned(true)
    assertFalse(fragment.isWritable)
  }

  fun testOriginalBecomesReadOnly() {
    assertTrue(fragment.isWritable)

    document.setReadOnly(true)
    assertFalse(fragment.isWritable)

    document.setReadOnly(false)
    assertTrue(fragment.isWritable)
  }

  fun testRemoveOriginalFragment() {
    assertTrue(fragment.isWritable)
    replaceString(document, 2, 8, "");
    assertEquals("0189", document.text);
    assertEquals("Invalid selection range", fragment.text)
    assertFalse(fragment.isWritable)
  }

  fun testRemoveListeners() {
    replaceString(fragment, 0, 1, "x");
    assertEquals("012x456789", document.text);

    fragmentContent.onAssigned(false)
    replaceString(fragment, 0, 1, "3");
    assertEquals("012x456789", document.text);
    replaceString(document, 3, 4, "y");
    assertEquals("012y456789", document.text);
    assertEquals("3456", fragment.text);

    fragmentContent.onAssigned(true)
  }

  private fun replaceString(document: Document, startOffset: Int, endOffset: Int, string: String) {
    ApplicationManager.getApplication().runWriteAction {
      CommandProcessor.getInstance().executeCommand(null, {
        document.replaceString(startOffset, endOffset, string)
      }, null, null)
    }
  }
}
