// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.DumbAwareAnnotationUtil
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
class DumbAwareAnnotationUtilTest {
  private val myFixture by javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/util/dumbAwareAnnotation/")
  private val project get() = myFixture.project

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false)
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations)
  }

  @Test
  fun testNonNls(): Unit = doFindAnnotationTest(AnnotationUtil.NON_NLS)

  @Test
  fun testNotNull(): Unit = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  @Test
  fun testNullable(): Unit = doFindAnnotationTest(AnnotationUtil.NULLABLE)

  @Test
  fun testAnnotationWithFqn(): Unit = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  @Test
  fun testAnnotationWithStarImport(): Unit = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  @Test
  fun testWrongImport(): Unit = doFindAnnotationTest(AnnotationUtil.NULLABLE, false)

  @Test
  fun testAnnotationInMethod(): Unit = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  @Test
  fun testFormatReferenceSimple(): Unit = doFormatReferenceTest("foo.bar", "foo.bar")

  @Test
  fun testFormatReferenceSpacesOnTheEnd(): Unit = doFormatReferenceTest("foo.  bar   ", "foo.bar")

  @Test
  fun testFormatReferenceSpacesInTheMiddle(): Unit = doFormatReferenceTest("foo.   bar  .baz", "foo.bar.baz")

  @Test
  fun testFormatReferenceSpacesInTheBeginning(): Unit = doFormatReferenceTest("  foo  .bar", "foo.bar")

  @Test
  fun testFormatReferenceWithNewLines(): Unit = doFormatReferenceTest("\nfoo\n.\n\nbar\n.baz", "foo.bar.baz")

  private fun doFormatReferenceTest(input: String, expected: String)  {
    val actual = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
      DumbAwareAnnotationUtil.getFormattedReferenceFqn(input)
    }
    assertEquals(expected, actual)
  }

  private fun doFindAnnotationTest(annotationFqn: String, shouldAnnotationBePresent: Boolean = true) {
    myFixture.configureByFile("$testName.java")
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      runReadActionBlocking {
        val element = myFixture.elementAtCaret
        assertTrue(element is PsiModifierListOwner)
        assertEquals(DumbAwareAnnotationUtil.hasAnnotation(element as PsiModifierListOwner, annotationFqn), shouldAnnotationBePresent)
      }
    }
  }
}