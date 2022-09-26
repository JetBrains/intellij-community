// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search

import com.intellij.JavaTestUtil
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPatternVariable
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.JavaPsiTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import java.lang.Integer.max

class FindUsagesJava19Test : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_19
  }

  fun testPatternSearch() {
    myFixture.configureByText("Foo.java", "sealed interface I {\n" +
                                          "  static int f(C c) {\n" +
                                          "    return switch (c) {\n" +
                                          "      case C(A(int <caret>n)) -> n;\n" +
                                          "      case C(B(int n)) -> n;\n" +
                                          "    };\n" +
                                          "  }\n" +
                                          "}\n" +
                                          "record C(I i) {}\n" +
                                          "record B(int n) implements I {}\n" +
                                          "record A(int n) implements I {}")

    val pattern = file.findElementAt(myFixture.caretOffset)!!.parentOfType<PsiPatternVariable>()!!
    val references = ReferencesSearch.search(pattern).toList()
    TestCase.assertEquals(1, references.size)

  }
}