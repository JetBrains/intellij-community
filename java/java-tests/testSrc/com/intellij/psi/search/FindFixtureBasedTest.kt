/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search

import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class FindFixtureBasedTest : LightCodeInsightFixtureTestCase() {
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
}
