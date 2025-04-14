// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.PropertyIndexValue
import com.intellij.psi.impl.javaSimplePropertyGist
import com.intellij.psi.util.PropertyMemberType
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlin.test.assertNotEquals

class JavaPropertyDetectionTest : LightJavaCodeInsightFixtureTestCase() {

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
    assertNotPropertyMember("""class Some extends SomeBase {public String getN<caret>ame() { return name; }}class SomeBase { private String name;}""", PropertyMemberType.GETTER)
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

  fun testParenthesesGetter() {
    assertPropertyMember("""
      class X {
        String prop;
        String getProp<caret>() {
         return ((X.this).prop);
        }
      }
      """, PropertyMemberType.GETTER)
  }

  // setter field test

  fun testSimpleSetter() {
    assertPropertyMember("""class Some {
      |private String name;   public void set<caret>Name(String name) { this.name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  fun testParenthesesSetter() {
    assertPropertyMember("""
      class Y {
        private String prop;
        void set<caret>Prop(String prop) {
          ((Y.this).prop) = (prop);
        }
      }
    """, PropertyMemberType.SETTER)
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

  // index data test

  fun testAddToIndexOnlyMethodCallsWithoutArguments() {
    assertJavaSimplePropertyIndex("""
      public class Foo {
        public String getName() {
          return name;
        }

        public String getName(int x) {
          return name;
        }

        public String getBoo() {
          return Boo.Foo.CONST;
        }

        public String getXxx() {
          return xxx().yyy;
        }

        public class Bar {
          public String getName() {
            return Foo.this.getName();
          }

          public String getName1() {
            return Foo.this.getName(100);
          }
        }
        
        class X {
          public String getX() {
            return (((x)));
          }
      }
    """.trimIndent(), Int2ObjectOpenHashMap<PropertyIndexValue>().apply {
      put(0, PropertyIndexValue("name", true))
      put(2, PropertyIndexValue("Boo.Foo.CONST", true))
      put(6, PropertyIndexValue("(((x)))", true))
    })
  }

  fun testIndexDoesntContainPolyadicExpressions() {
    assertJavaSimplePropertyIndex("""
      public class Foo {
        public int getX() { x + 1; }
      
        public String getName() {
          return n + a + m + e;
        }

        public String getName1() {
          return --i;
        }

        public String getName2() {
          return 1 == 1 ? 1 : 1;
        }

        public String getName3() {
          return new String();
        }
      }
    """.trimIndent(), Int2ObjectOpenHashMap())
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
    val method = PsiTreeUtil.getNonStrictParentOfType(myFixture.elementAtCaret, PsiMethod::class.java)!!
    //use index
    assertEquals(expectedDecision, if (memberType == PropertyMemberType.GETTER) PropertyUtil.isSimpleGetter(method) else PropertyUtil.isSimpleSetter(method))
    //use ast
    assertEquals(expectedDecision, if (memberType == PropertyMemberType.GETTER) PropertyUtil.isSimpleGetter(method, false) else PropertyUtil.isSimpleSetter(method, false))
  }

  private fun assertJavaSimplePropertyIndex(text: String, expected: Int2ObjectMap<PropertyIndexValue>) {
    val file = myFixture.configureByText(JavaFileType.INSTANCE, text)
    val data = javaSimplePropertyGist.getFileData(file)
    assertEquals(expected, data)
  }
}
