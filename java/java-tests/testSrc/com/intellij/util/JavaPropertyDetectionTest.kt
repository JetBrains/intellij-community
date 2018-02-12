/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PropertyMemberType
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import kotlin.test.assertNotEquals

class JavaPropertyDetectionTest : LightCodeInsightFixtureTestCase() {

  // getter field test

  fun testFieldRef() {
    assertPropertyMember("""class Some {private String name;public String getN<caret>ame() { return name; }}""", PropertyMemberType.GETTER) 
  }

  fun testUnresolvedRef() {
    assertNotPropertyMember("class Some { public String getN<caret>ame() { return name; }}", PropertyMemberType.GETTER) 
  }

  fun testUnresolvedRef2() {
    assertNotPropertyMember("""class Some {private String name;public static String getN<caret>ame() { return name; }}""", PropertyMemberType.GETTER) 
  }

  fun testSuperFieldRef() {
    doTest("""class Some extends SomeBase {public String getN<caret>ame() { return name; }}class SomeBase { private String name;}""", PropertyMemberType.GETTER,
           false)
  }

  fun testSuperFieldRef2() {
    assertPropertyMember("""class Some extends SomeBase
      |{public String getN<caret>ame() { return name; }}class SomeBase { protected String name;}""".trimMargin(), PropertyMemberType.GETTER)
  }

  fun testInconsistentType() {
    assertNotPropertyMember("""class Some
      |{String name;public long getNa<caret>me() { return name;}}""".trimMargin(), PropertyMemberType.GETTER)

  }

  fun testWithSuperKeyWord() {
    assertPropertyMember("""import java.util.List; class Some extends SomeBase
      |{ List<String> getL<caret>ist() { return Some.super.list;}} class SomeBase {List<String> list;} """.trimMargin(), PropertyMemberType.GETTER)
  }

  fun testRedundantParenthesises() {
    assertNotPropertyMember("""import java.util.List;
                            class Some {
                                Some a;
                                List<String> list;
                                List<String> getL<caret>ist() {return (a).list;}
                            }""", PropertyMemberType.GETTER)
  }



  // setter field test

  fun testSimpleSetter() {
    assertPropertyMember("""class Some {
      |private String name;   public void set<caret>Name(String name) { this.name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testFieldNotResolved() {
    assertNotPropertyMember("""class Some {
      | public void set<caret>Name(String name) { this.name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testInvalidExpression() {
    assertNotPropertyMember("""class Some {
      |public void set<caret>Name(String name) { = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testInvalidReference() {
    assertNotPropertyMember("""class Some {
      |public void set<caret>Name(String name) { name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testSuperClassField() {
    assertPropertyMember(   """class Some extends SomeBase {
      | public void set<caret>Name(String name) { this.name = name; }}
      | class SomeBase {protected String name;}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testRefWithSuperKeyword() {
    assertPropertyMember(   """class Some extends SomeBase {
      |public void set<caret>Name(String name) { Some.super.name = name; }}
      | class SomeBase {protected String name;}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testOuterClassFieldSetter() {
    assertNotPropertyMember("""class Outer {String name;   class Some {
      |public void set<caret>Name(String name) { this.name = name; }}}""".trimMargin(), PropertyMemberType.SETTER)
  }

  private fun assertPropertyMember(text: String, memberType: PropertyMemberType) {
    doTest(text, memberType, true)
  }
  
  private fun assertNotPropertyMember(text: String, memberType: PropertyMemberType) {
    doTest(text, memberType, false)
  }
  
  private fun doTest(text: String, memberType: PropertyMemberType, expectedDecision: Boolean) {
    assertNotEquals(PropertyMemberType.FIELD, memberType)
    myFixture.configureByText(JavaFileType.INSTANCE, text)
    val method = PsiTreeUtil.getNonStrictParentOfType(myFixture.elementAtCaret, PsiMethod::class.java)
    assertNotNull(method)
    //use index
    assertEquals(expectedDecision, if (memberType == PropertyMemberType.GETTER) PropertyUtil.isSimpleGetter(method) else PropertyUtil.isSimpleSetter(method))
    //use ast
    assertEquals(expectedDecision, if (memberType == PropertyMemberType.GETTER) PropertyUtil.isSimpleGetter(method, false) else PropertyUtil.isSimpleSetter(method, false))
  }
}
