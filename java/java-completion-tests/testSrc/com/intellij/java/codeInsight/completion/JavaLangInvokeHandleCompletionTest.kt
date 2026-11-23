// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.math.min

@NeedsIndex.Full
@TestApplication
@IndexingModeTemplate
class JavaLangInvokeHandleCompletionTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9)
  }

  private val completion by lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/invokeHandle/")
  private val myFixture get() = completion.fixture

  @Test
  fun testVirtual(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestFirst(1, "m1(int)", "pm1(int)", "m2(float, double)") }
  @Test
  fun testVirtualPrefixed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(1, "m1(int)", "m2(float, double)", "pm1(int)") }

  @Test
  fun testStatic(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(0, "psm1(char)", "sm1(char)", "sm2(short)") }

  @Test
  fun testGetter(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(0, "f1", "pf1", "f2") }
  @Test
  fun testSetter(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(2, "f1", "pf1", "f2") }

  @Test
  fun testStaticGetter(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(0, "psf1", "sf1", "sf2") }
  @Test
  fun testStaticSetter(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(2, "psf1", "sf1", "sf2") }

  @Test
  fun testVarHandle(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(0, "f1", "pf1", "f2") }
  @Test
  fun testStaticVarHandle(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(0, "psf1", "sf1", "sf2") }

  @Test
  fun testOverloaded(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(1, "strMethod()", "strMethod(int, int)") }

  @Test
  fun testVirtualType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "MethodType.methodType(String.class)", "MethodType.methodType(String.class, int.class, int.class)") }
  @Test
  fun testStaticType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(1,
                                     "MethodType.methodType(Object.class, int.class, int.class)",
                                     "MethodType.methodType(Object.class, Object.class)") }

  @Test
  fun testGetterType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "int.class") }
  @Test
  fun testSetterType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "float.class") }

  @Test
  fun testVarHandleType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "String.class") }
  @Test
  fun testStaticVarHandleType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "Object.class") }

  @Test
  fun testVirtualTypeGeneric(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "MethodType.methodType(Object.class, Object.class, String.class)") }
  @Test
  fun testStaticTypeGeneric(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "MethodType.methodType(Object.class, List.class, Object[].class)") }

  @Test
  fun testConstructorType1(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0, "MethodType.methodType(void.class)") }
  @Test
  fun testConstructorType2(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(0,
                                           "MethodType.methodType(void.class)",
                                           "MethodType.methodType(void.class, int.class)",
                                           "MethodType.methodType(void.class, List.class)",
                                           "MethodType.methodType(void.class, Object[].class)") }
  @Test
  fun testConstructorType3(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTestTypes(3,
                                           "MethodType.methodType(void.class)",
                                           "MethodType.methodType(void.class, int.class)",
                                           "MethodType.methodType(void.class, List.class)",
                                           "MethodType.methodType(void.class, Object[].class)") }

  private fun doTest(index: Int, vararg expected: String) {
    doTest(index, { assertLookupTexts(false, *expected) })
  }

  private fun doTestFirst(index: Int, vararg expected: String) {
    doTest(index, { assertLookupTexts(true, *expected, "clone()") })
  }

  private fun doTestTypes(index: Int, vararg expected: String) {
    myFixture.addClass("""
import java.util.List;
public class Types extends Parent {
  String str;
  static Object sObj;

  String strMethod() {return "";}
  String strMethod(int n, int m) {return "";}
  static String strMethod(int n) {return "";}

  static Object objMethod(Object o) {return o;}
  static Object objMethod(int n, int m) {return n;}
  Object objMethod(int n) {return n;}

  <T> T genericMethod(T t, String s) {return t;}
  static <T> T sGenericMethod(List<T> lst, T... ts) {return ts[0];}
}""")
    myFixture.addClass("""
import java.util.List;
public class Constructed<T> {
  Constructed() {}
  Constructed(int n) {}
  Constructed(List<T> a) {}
  Constructed(T... a) {}
}""")

    doTest(index, { assertLookupTexts(true, *expected) })
  }

  private fun assertLookupTexts(compareFirst: Boolean, vararg expected: String) {
    val elements = myFixture.lookupElements
    assertNotNull(elements)
    val lookupTexts = elements!!.map {
      val presentation = NormalCompletionFixture.renderElement(it)
      (presentation.itemText ?: "") + (presentation.tailText ?: "")
    }

    val actual = if (compareFirst) lookupTexts.subList(0, min(expected.size, lookupTexts.size)) else lookupTexts
    assertIterableEquals(expected.toList(), actual)
  }

  private fun doTest(index: Int, assertion: () -> Unit) {
    myFixture.addClass("""
public class Parent {
  public int pf1;
  private float pf2;
  public static char psf1;
  private static short psf2;

  public void pm1(int n) {}
  private void pm2(float n, double m) {}
  public static void psm1(char n) {}
  private static void psm2(short n) {}
}""")
    myFixture.addClass("""
public class Test extends Parent {
  public int f1;
  private float f2;
  public static char sf1;
  private static short sf2;

  public void m1(int n) {}
  private void m2(float n, double m) {}
  public static void sm1(char n) {}
  private static void sm2(short n) {}
}""")

    completion.configureByFile(myFixture.getTestName(false) + ".java")
    assertion()
    if (index >= 0) completion.selectItem(completion.getLookup()!!.items[index])
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java")
  }
}
