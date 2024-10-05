// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.DumbAwareAnnotationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class DumbAwareAnnotationUtilTest : JavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = JavaTestUtil.getJavaTestDataPath() + "/util/dumbAwareAnnotation/"

  override fun setUp() {
    super.setUp()
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations)
  }

  fun testNonNls() = doFindAnnotationTest(AnnotationUtil.NON_NLS)

  fun testNotNull() = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  fun testNullable() = doFindAnnotationTest(AnnotationUtil.NULLABLE)

  fun testAnnotationWithFqn() = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  fun testAnnotationWithStarImport() = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  fun testWrongImport() = doFindAnnotationTest(AnnotationUtil.NULLABLE, false)

  fun testAnnotationInMethod() = doFindAnnotationTest(AnnotationUtil.NOT_NULL)

  fun testFormatReferenceSimple() = doFormatReferenceTest("foo.bar", "foo.bar")

  fun testFormatReferenceSpacesOnTheEnd() = doFormatReferenceTest("foo.  bar   ", "foo.bar")

  fun testFormatReferenceSpacesInTheMiddle() = doFormatReferenceTest("foo.   bar  .baz", "foo.bar.baz")

  fun testFormatReferenceSpacesInTheBeginning() = doFormatReferenceTest("  foo  .bar", "foo.bar")

  fun testFormatReferenceWithNewLines() = doFormatReferenceTest("\nfoo\n.\n\nbar\n.baz", "foo.bar.baz")

  private fun doFormatReferenceTest(input: String, expected: String)  {
    val actual = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
      DumbAwareAnnotationUtil.getFormattedReferenceFqn(input)
    }
    assertEquals(expected, actual)
  }

  private fun doFindAnnotationTest(annotationFqn: String, shouldAnnotationBePresent: Boolean = true) {
    myFixture.configureByFile(getTestName(false) + ".java")
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      val element = myFixture.elementAtCaret
      assertTrue(element is PsiModifierListOwner)
      assertEquals(DumbAwareAnnotationUtil.hasAnnotation(element as PsiModifierListOwner, annotationFqn), shouldAnnotationBePresent)
    }
  }
}