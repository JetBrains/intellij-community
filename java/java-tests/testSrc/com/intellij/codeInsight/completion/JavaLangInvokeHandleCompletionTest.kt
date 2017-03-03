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
package com.intellij.codeInsight.completion

import com.intellij.JavaTestUtil

/**
 * @author Pavel Dolgov
 */
class JavaLangInvokeHandleCompletionTest : LightFixtureCompletionTestCase() {

  fun testVirtual() = doTestFirst(1, "m1", "pm1", "m2")
  fun testVirtualPrefixed() = doTest(1, "m1", "m2", "pm1")

  fun testStatic() = doTest(0, "psm1", "sm1", "sm2")

  fun testGetter() = doTest(0, "f1", "pf1", "f2")
  fun testSetter() = doTest(2, "f1", "pf1", "f2")

  fun testStaticGetter() = doTest(0, "psf1", "sf1", "sf2")
  fun testStaticSetter() = doTest(2, "psf1", "sf1", "sf2")

  // TODO enable when the mock for jdk9 is available
  fun _testVarHandle() = doTest(0, "f1", "pf1", "f2")
  fun _testStaticVarHandle() = doTest(0, "psf1", "sf1", "sf2")

  override fun getBasePath(): String {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/invokeHandle/"
  }


  private fun doTest(index: Int, vararg expected: String) {
    doTest(index, { assertStringItems(*expected) })
  }

  private fun doTestFirst(index: Int, vararg expected: String) {
    doTest(index, { assertFirstStringItems(*expected, "clone") })
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

    configureByFile(getTestName(false) + ".java")
    assertion()
    if (index >= 0) selectItem(lookup.items[index])
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }
}
