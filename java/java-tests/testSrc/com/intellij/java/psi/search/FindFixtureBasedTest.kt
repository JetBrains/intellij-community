// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.usages.rules.PsiElementUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class FindFixtureBasedTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testDefaultConstructorWithVarargsParameters() {
    val aClass = myFixture.addClass("public class Foo {public Foo(int... a) {}}")
    myFixture.addClass("public class Bar extends Foo {}")

    val constructors = runReadActionBlocking { aClass.constructors }
    assertEquals(1, constructors.size)
    val references = runReadActionBlocking { MethodReferencesSearch.search(constructors[0]).findAll() }
    assertEquals(1, references.size)
  }

  @Test
  fun testDefaultConstructorNotInvoked() {
    val aClass = myFixture.addClass("public class A {\n" +
                                    "  public A() {}\n" +
                                    "  public A(String s){}\n" +
                                    "  public A(int i) {this(null);}\n" +
                                    "}")
    myFixture.addClass("class B extends A {public B(){this(\"\");} public B(String s) {super(s);} }")

    val constructors = runReadActionBlocking { aClass.constructors }
    assertEquals(3, constructors.size)
    assertNull(MethodReferencesSearch.search(constructors[0]).findFirst())
  }
  
  @Test
  fun testFlexibleConstructorBody() {
    val aClass = myFixture.addClass("class Friend {  Friend() {}}");
    myFixture.addClass("class Flexible extends Friend {\n" +
                       "  Flexible() {\n" +
                       "    System.out.println(\"before\");\n" +
                       "    super();\n" +
                       "    System.out.println(\"after\");\n" +
                       "  }\n" +
                       "}")
    
    val constructors = runReadActionBlocking { aClass.constructors }
    assertEquals(1, constructors.size)
    val reference = MethodReferencesSearch.search(constructors[0]).findFirst()
    assertTrue(reference?.element is PsiReferenceExpression)
  }
  
  @Test
  fun testFindEnumConstructorUsages(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("LastOfItsKinds.java", """
      public enum LastOfItsKind<caret> {
        A, B;

        LastOfItsKind() {
        }
      }
    """.trimIndent())
    val usages = myFixture.testFindUsagesUsingAction()
    val result = readAction { usages.map { u -> if (u is PsiElementUsage) u.element.text else "" } }
    assertEquals(listOf("A", "B"), result)
  }
}
