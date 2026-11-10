// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiPatternVariable
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_19
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class FindUsagesJava19Test {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_19)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testPatternSearch(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

    val pattern = readAction { myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<PsiPatternVariable>()!! }
    val references = ReferencesSearch.search(pattern).asIterable().toList()
    assertEquals(1, references.size)
  }
}