// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search

import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.usages.rules.PsiElementUsage

class FindFixtureBasedTest : LightJavaCodeInsightFixtureTestCase() {
  fun testDefaultConstructorWithVarargsParameters() {
    val aClass = myFixture.addClass("public class Foo {public Foo(int... a) {}}")
    myFixture.addClass("public class Bar extends Foo {}")

    val constructors = aClass.constructors
    assertSize(1, constructors)
    val references = MethodReferencesSearch.search(constructors[0]).findAll()
    assertSize(1, references)
  }

  fun testDefaultConstructorNotInvoked() {
    val aClass = myFixture.addClass("public class A {\n" +
                                    "  public A() {}\n" +
                                    "  public A(String s){}\n" +
                                    "  public A(int i) {this(null);}\n" +
                                    "}")
    myFixture.addClass("class B extends A {public B(){this(\"\");} public B(String s) {super(s);} }")

    val constructors = aClass.constructors
    assertSize(3, constructors)
    assertNull(MethodReferencesSearch.search(constructors[0]).findFirst())
  }
  
  fun testFlexibleConstructorBody() {
    val aClass = myFixture.addClass("class Friend {  Friend() {}}");
    myFixture.addClass("class Flexible extends Friend {\n" +
                       "  Flexible() {\n" +
                       "    System.out.println(\"before\");\n" +
                       "    super();\n" +
                       "    System.out.println(\"after\");\n" +
                       "  }\n" +
                       "}")
    
    val constructors = aClass.constructors
    assertSize(1, constructors)
    val reference = MethodReferencesSearch.search(constructors[0]).findFirst()
    assertTrue(reference?.element is PsiReferenceExpression)
  }
  
  fun testFindEnumConstructorUsages() {
    myFixture.configureByText("LastOfItsKinds.java", """
      public enum LastOfItsKind<caret> {
        A, B;

        LastOfItsKind() {
        }
      }
    """.trimIndent())
    val usages = myFixture.testFindUsagesUsingAction()
    val result = usages.map { u -> if (u is PsiElementUsage) u.element.text else "" }
    assertEquals(listOf("A", "B"), result)
  }
}
