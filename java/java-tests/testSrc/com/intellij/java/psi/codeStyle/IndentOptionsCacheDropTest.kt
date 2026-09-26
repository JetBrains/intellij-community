// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider
import com.intellij.psi.codeStyle.TimeStampedIndentOptions
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat


class IndentOptionsCacheDropTest: LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    val instance = DetectableIndentOptionsProvider.getInstance()
    instance?.setEnabledInTest(true)
  }

  override fun tearDown() {
    try {
      DetectableIndentOptionsProvider.getInstance()?.setEnabledInTest(false)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  val code = """
class Test {
  public void test() {<caret>
    int a = 2;
  }
}
"""

  fun `test store valid timestamped options in document when detecting indents`() {
    val file = PsiFileFactory.getInstance(project).createFileFromText("Test.java", JavaFileType.INSTANCE, code, 0, true)
    val detectableOptionsProvider = object : DetectableIndentOptionsProvider() {
      override fun scheduleDetectionInBackground(project: Project, document: Document, indentOptions: TimeStampedIndentOptions, settings: CodeStyleSettings) {
        //just do nothing, so default indent options will be kept (same as very long indent detection calculation)
      }
    }
    detectableOptionsProvider.setEnabledInTest(true)

    val settings = CodeStyle.getSettings(project)
    val options = detectableOptionsProvider.getIndentOptions(file.project, settings, file.virtualFile)

    val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
    val indentOptions = detectableOptionsProvider.getValidCachedIndentOptions(file.project, file.virtualFile, document, settings)!!

    @Suppress("SuspiciousEqualsCombination")
    assert(options == indentOptions && options === indentOptions)
  }

  fun testDropIndentOptions_WhenTabSizeChanged() {
    val current = CodeStyle.getSettings(project)
    val options = current.getCommonSettings(JavaLanguage.INSTANCE).indentOptions!!
    myFixture.configureByText(JavaFileType.INSTANCE, code)

    val tabSize = myFixture.editor.settings.getTabSize(project)
    assertThat(tabSize).isEqualTo(options.TAB_SIZE)
    
    options.TAB_SIZE = 14
    
    reinitEditorSettings()
    
    val newTabSize = myFixture.editor.settings.getTabSize(project)
    assertThat(newTabSize).isEqualTo(14)
  }

  fun testIndentOptionsCache_NotDroppedOnReinit() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val document = myFixture.getDocument(file);
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(document)!!

    reinitEditorSettings()
    
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(document)).isTrue()
  }
  
  fun testIndentOptionsCache_NotDroppedOnChange() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val document = myFixture.getDocument(file);
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(document)!!

    myFixture.type(" abracadabra")
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(document)).isTrue()
  }

  fun testIndentOptionsDrop_OnDocumentChangeAndReinit() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val document = myFixture.getDocument(file);
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(document)!!

    myFixture.type(" abracadabra")
    
    reinitEditorSettings()
    
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(document)).isFalse()
  }
  
  private fun reinitEditorSettings() = (myFixture.editor as EditorImpl).reinitSettings()

}