// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.PropertyIndexValue
import com.intellij.psi.impl.javaSimplePropertyGist
import com.intellij.psi.util.PropertyMemberType
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaPropertyDetectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  // getter field test

  @Test
  fun testFieldRef(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember("""class Some {private String name;public String getN<caret>ame() { return name; }}""", PropertyMemberType.GETTER)
  }

  @Test
  fun testUnresolvedRef(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("class Some { public String getN<caret>ame() { return name; }}", PropertyMemberType.GETTER)
  }

  @Test
  fun testUnresolvedRef2(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Some {private String name;public static String getN<caret>ame() { return name; }}""", PropertyMemberType.GETTER)
  }

  @Test
  fun testSuperFieldRef(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Some extends SomeBase {public String getN<caret>ame() { return name; }}class SomeBase { private String name;}""", PropertyMemberType.GETTER)
  }

  @Test
  fun testSuperFieldRef2(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember("""class Some extends SomeBase
      |{public String getN<caret>ame() { return name; }}class SomeBase { protected String name;}""".trimMargin(), PropertyMemberType.GETTER)
  }

  @Test
  fun testInconsistentType(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Some
      |{String name;public long getNa<caret>me() { return name;}}""".trimMargin(), PropertyMemberType.GETTER)

  }

  @Test
  fun testWithSuperKeyWord(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember("""import java.util.List; class Some extends SomeBase
      |{ List<String> getL<caret>ist() { return Some.super.list;}} class SomeBase {List<String> list;} """.trimMargin(), PropertyMemberType.GETTER)
  }

  @Test
  fun testRedundantParenthesises(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""import java.util.List;
                            class Some {
                                Some a;
                                List<String> list;
                                List<String> getL<caret>ist() {return (a).list;}
                            }""", PropertyMemberType.GETTER)
  }

  @Test
  fun testParenthesesGetter(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun testSimpleSetter(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember("""class Some {
      |private String name;   public void set<caret>Name(String name) { this.name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  @Test
  fun testParenthesesSetter(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember("""
      class Y {
        private String prop;
        void set<caret>Prop(String prop) {
          ((Y.this).prop) = (prop);
        }
      }
    """, PropertyMemberType.SETTER)
  }

  @Test
  fun testFieldNotResolved(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Some {
      | public void set<caret>Name(String name) { this.name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  @Test
  fun testInvalidExpression(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Some {
      |public void set<caret>Name(String name) { = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  @Test
  fun testInvalidReference(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Some {
      |public void set<caret>Name(String name) { name = name; }}""".trimMargin(), PropertyMemberType.SETTER)
  }

  @Test
  fun testSuperClassField(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember(   """class Some extends SomeBase {
      | public void set<caret>Name(String name) { this.name = name; }}
      | class SomeBase {protected String name;}""".trimMargin(), PropertyMemberType.SETTER)
  }

  @Test
  fun testRefWithSuperKeyword(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertPropertyMember(   """class Some extends SomeBase {
      |public void set<caret>Name(String name) { Some.super.name = name; }}
      | class SomeBase {protected String name;}""".trimMargin(), PropertyMemberType.SETTER)
  }

  @Test
  fun testOuterClassFieldSetter(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNotPropertyMember("""class Outer {String name;   class Some {
      |public void set<caret>Name(String name) { this.name = name; }}}""".trimMargin(), PropertyMemberType.SETTER)
  }

  // index data test

  @Test
  fun testAddToIndexOnlyMethodCallsWithoutArguments(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun testIndexDoesntContainPolyadicExpressions(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  private suspend fun assertPropertyMember(text: String, memberType: PropertyMemberType) {
    doTest(text, memberType, true)
  }

  private suspend fun assertNotPropertyMember(text: String, memberType: PropertyMemberType) {
    doTest(text, memberType, false)
  }

  private suspend fun doTest(text: String, memberType: PropertyMemberType, expectedDecision: Boolean) {
    assertNotEquals(PropertyMemberType.FIELD, memberType)
    myFixture.configureByText(JavaFileType.INSTANCE, text)
    readAction {
      val method = PsiTreeUtil.getNonStrictParentOfType(myFixture.elementAtCaret, PsiMethod::class.java)!!
      //use index
      assertEquals(expectedDecision, if (memberType == PropertyMemberType.GETTER) PropertyUtil.isSimpleGetter(method) else PropertyUtil.isSimpleSetter(method))
      //use ast
      assertEquals(expectedDecision, if (memberType == PropertyMemberType.GETTER) PropertyUtil.isSimpleGetter(method, false) else PropertyUtil.isSimpleSetter(method, false))
    }
  }

  private suspend fun assertJavaSimplePropertyIndex(text: String, expected: Int2ObjectMap<PropertyIndexValue>) {
    val file = myFixture.configureByText(JavaFileType.INSTANCE, text)
    val data = readAction { javaSimplePropertyGist.getFileData(file) }
    assertEquals(expected, data)
  }
}
