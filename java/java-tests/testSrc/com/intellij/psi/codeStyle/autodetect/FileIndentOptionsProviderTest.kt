/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.*

class ViewerEditorTest: LightPlatformTestCase() {
  lateinit var mockProvider: FileIndentOptionsProvider
  lateinit var extensionPoint: ExtensionPoint<FileIndentOptionsProvider>
  
  override fun setUp() {
    super.setUp()
    extensionPoint = Extensions.getRootArea().getExtensionPoint(FileIndentOptionsProvider.EP_NAME)
    mockProvider = mock(FileIndentOptionsProvider::class.java)
    extensionPoint.registerExtension(mockProvider, LoadingOrder.FIRST)
  }

  override fun tearDown() {
    extensionPoint.unregisterExtension(mockProvider)
    super.tearDown()
  }

  @Test
  fun `test do not use file indent option providers for viewer editors`() {
    val file = createFile("Test.java", "class Test {}")
    val document = PsiDocumentManager.getInstance(getProject()).getDocument(file)
    val editor = EditorFactory.getInstance().createEditor(document!!, getProject(), file.virtualFile, true);
    try {
      (editor as EditorImpl).reinitSettings()
      editor.settings.isLineNumbersShown = true
      editor.settings.getTabSize(getProject())
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    verify(mockProvider, never()).getIndentOptions(Matchers.any(), Matchers.any())
  }
  
}