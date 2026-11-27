// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.codeStyle.autodetect

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.LocalTimeCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@TestApplication
class ViewerEditorTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val project get() = projectFixture.get().project

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  private lateinit var mockProvider: FileIndentOptionsProvider

  @BeforeEach
  fun setUp() {
    mockProvider = mock(FileIndentOptionsProvider::class.java)
    ExtensionTestUtil.maskExtensions(FileIndentOptionsProvider.EP_NAME, listOf(mockProvider), testRootDisposable)
  }

  @Test
  fun `test do not use file indent option providers for viewer editors`(): Unit = timeoutRunBlocking {
    val file = readAction {
      PsiFileFactory.getInstance(project).createFileFromText("Test.java", FileTypeManager.getInstance().getFileTypeByFileName("Test.java"),
                                                             "class Test {}", LocalTimeCounter.currentTime(), true, false)
    }
    val document = readAction { PsiDocumentManager.getInstance(project).getDocument(file) }
    withContext(Dispatchers.EDT) {
      val editor = EditorFactory.getInstance().createEditor(document!!, project, file.virtualFile, true)
      try {
        (editor as EditorImpl).reinitSettings()
        editor.settings.isLineNumbersShown = true
        editor.settings.getTabSize(project)
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
    verify(mockProvider, never()).getIndentOptions(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }
}
