// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.JavaTestUtil
import com.intellij.application.options.CodeStyle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.WRAP_ALWAYS
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JSpecifyTypeAnnotationFormatterTest {
  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/psi/formatter/java/jSpecifyTypeAnnotation/"
  }

  companion object {
    private val PROJECT_DESCRIPTOR = DefaultLightProjectDescriptor()
      .withJetBrainsAnnotationsWithTypeUse()
      .withRepositoryLibrary("org.jspecify:jspecify:1.0.0")
    private val projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR)
  }

  private val commonSettings: CommonCodeStyleSettings
    get() = CodeStyle.getSettings(myFixture.project).getCommonSettings(JavaLanguage.INSTANCE)

  @BeforeEach
  fun setUp() {
    commonSettings.KEEP_LINE_BREAKS = false
    commonSettings.METHOD_ANNOTATION_WRAP = WRAP_ALWAYS
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_8, myFixture.testRootDisposable)
  }

  @Test
  fun testKnownAnnotationBeforeType(): Unit = doTest()

  @Test
  fun testKnownAnnotationBeforeTypeParameterList(): Unit = doTest()

  @Test
  fun testCustomAnnotation(): Unit = doTest()

  @Test
  fun testManyKnownAnnotations(): Unit = doTest()

  @Test
  fun testPreserveWrappingSingleAnnotation(): Unit = doTest()

  @Test
  fun testPreserveWrappingManyAnnotations(): Unit = doTest()

  @Test
  fun testFullyQualifiedName(): Unit = doTest()

  @Test
  fun testImportOnDemand(): Unit = doTest()

  @Test
  fun testImportMix(): Unit = doTest()

  @Test
  fun testSpacesInImportList(): Unit = doTest()

  @Test
  fun testSpacesInFqnAnnotations(): Unit = doTest()

  @Test
  fun testKeepLineBreaks() {
    commonSettings.KEEP_LINE_BREAKS = true
    doTest()
  }

  @Test
  fun testLowLanguageLevel() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_7)
    doTest()
  }

  @Test
  fun testModuleImport() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_25)
    doTest()
  }

  @Test
  fun testModuleImportWithSpaces() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_25)
    doTest()
  }

  @Test
  fun testModuleImportMixedWithPackageImport() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_25)
    doTest()
  }

  @Test
  fun testModuleImportMixedWithFqn() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_25)
    doTest()
  }

  @Test
  fun testLowLanguageLevelForModuleImport() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_24)
    doTest()
  }

  @Test
  fun testUnknownModuleImport() {
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_25)
    doTest()
  }

  private fun doTest() {
    val testName = myFixture.getTestName(false)
    myFixture.configureByFile("$testName.java")
    WriteCommandAction.runWriteCommandAction(myFixture.project) { CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, 0, myFixture.editor.document.textLength) }
    myFixture.checkResultByFile("${testName}_after.java")
  }
}
