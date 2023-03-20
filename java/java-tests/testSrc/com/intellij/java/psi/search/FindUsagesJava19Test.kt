// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search

import com.intellij.psi.PsiPatternVariable
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert

class FindUsagesJava19Test : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_19
  }

  fun testPatternSearch() {
    myFixture.configureByText("Foo.java", """sealed interface I {
  static int f(C c) {
    return switch (c) {
      case C(A(int <caret>n)) -> n;
      case C(B(int n)) -> n;
    };
  }
}
record C(I i) {}
record B(int n) implements I {}
record A(int n) implements I {}""")

    val pattern = file.findElementAt(myFixture.caretOffset)!!.parentOfType<PsiPatternVariable>()!!
    val references = ReferencesSearch.search(pattern).toList()
    Assert.assertEquals(1, references.size)
  }
}