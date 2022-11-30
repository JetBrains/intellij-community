// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity

import com.intellij.psi.util.ProximityLocation
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JavaInheritanceWeigherTest : LightJavaCodeInsightFixtureTestCase() {
  fun `test non-class weighs same as non-super class`() {
    // KTIJ-23617: PsiClass elements which aren't super classes targeted by this weigher should not be ranked above non-PsiClass elements.
    myFixture.configureByText("A.java", """
      package pkg;

      class A {
        public void met<caret>hod() { }
      }
    """.trimIndent())

    val classElement = myFixture.findClass("pkg.A")
    val methodElement = myFixture.elementAtCaret
    val proximityLocation = ProximityLocation(classElement.containingFile, myFixture.module)

    val weigher = JavaInheritanceWeigher()

    val classWeight = weigher.weigh(classElement, proximityLocation)!!
    val methodWeight = weigher.weigh(methodElement, proximityLocation)!!

    assertEquals(0, classWeight.compareTo(methodWeight))
  }
}
