// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider
import com.intellij.psi.codeStyle.TimeStampedIndentOptions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class IndentOptionsCacheDropTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    val instance = DetectableIndentOptionsProvider.getInstance()
    instance?.setEnabledInTest(true)
  }

  @AfterEach
  fun tearDown() {
    DetectableIndentOptionsProvider.getInstance()?.setEnabledInTest(false)
  }

  val code = """
class Test {
  public void test() {<caret>
    int a = 2;
  }
}
"""

  @Test
  fun `test store valid timestamped options in document when detecting indents`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = readAction { PsiFileFactory.getInstance(myFixture.project).createFileFromText("Test.java", JavaFileType.INSTANCE, code, 0, true) }
    val detectableOptionsProvider = object : DetectableIndentOptionsProvider() {
      override fun scheduleDetectionInBackground(project: Project, document: Document, indentOptions: TimeStampedIndentOptions, settings: CodeStyleSettings) {
        //just do nothing, so default indent options will be kept (same as very long indent detection calculation)
      }
    }
    detectableOptionsProvider.setEnabledInTest(true)

    val settings = CodeStyle.getSettings(myFixture.project)
    val options = readAction { detectableOptionsProvider.getIndentOptions(file.project, settings, file.virtualFile) }

    val document = PsiDocumentManager.getInstance(myFixture.project).getDocument(file)!!
    val indentOptions = detectableOptionsProvider.getValidCachedIndentOptions(file.project, file.virtualFile, document, settings)!!

    @Suppress("SuspiciousEqualsCombination")
    assert(options == indentOptions && options === indentOptions)
  }

  @Test
  fun testDropIndentOptions_WhenTabSizeChanged(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val current = CodeStyle.getSettings(myFixture.project)
    val options = current.getCommonSettings(JavaLanguage.INSTANCE).indentOptions!!
    myFixture.configureByText(JavaFileType.INSTANCE, code)

    val tabSize = myFixture.editor.settings.getTabSize(myFixture.project)
    assertThat(tabSize).isEqualTo(options.TAB_SIZE)
    
    options.TAB_SIZE = 14
    
    reinitEditorSettings()
    
    val newTabSize = myFixture.editor.settings.getTabSize(myFixture.project)
    assertThat(newTabSize).isEqualTo(14)
  }

  @Test
  fun testIndentOptionsCache_NotDroppedOnReinit(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val document = myFixture.getDocument(myFixture.file);
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(document)!!

    reinitEditorSettings()
    
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(document)).isTrue()
  }
  
  @Test
  fun testIndentOptionsCache_NotDroppedOnChange() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val document = myFixture.getDocument(myFixture.file);
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(document)!!

    myFixture.type(" abracadabra")
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(document)).isTrue()
  }

  @Test
  fun testIndentOptionsDrop_OnDocumentChangeAndReinit(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val document = myFixture.getDocument(myFixture.file);
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(document)!!

    myFixture.type(" abracadabra")
    
    reinitEditorSettings()
    
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(document)).isFalse()
  }
  
  private suspend fun reinitEditorSettings() = withContext(Dispatchers.EDT) { (myFixture.editor as EditorImpl).reinitSettings() }

}