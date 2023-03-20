// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.codeStyle.autodetect

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*

class ViewerEditorTest: LightPlatformTestCase() {
  private lateinit var mockProvider: FileIndentOptionsProvider

  override fun setUp() {
    super.setUp()
    mockProvider = mock(FileIndentOptionsProvider::class.java)
    ExtensionTestUtil.maskExtensions(FileIndentOptionsProvider.EP_NAME, listOf(mockProvider), testRootDisposable)
  }

  @Test
  fun `test do not use file indent option providers for viewer editors`() {
    val file = createFile("Test.java", "class Test {}")
    val document = PsiDocumentManager.getInstance(getProject()).getDocument(file)
    val editor = EditorFactory.getInstance().createEditor(document!!, getProject(), file.virtualFile, true)
    try {
      (editor as EditorImpl).reinitSettings()
      editor.settings.isLineNumbersShown = true
      editor.settings.getTabSize(getProject())
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    verify(mockProvider, never()).getIndentOptions(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }
}