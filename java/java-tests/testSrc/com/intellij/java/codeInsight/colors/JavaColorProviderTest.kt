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
package com.intellij.java.codeInsight.colors

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.ColorIcon

/**
 * @author Konstantin Bulenkov
 */
class JavaColorProviderTest : LightCodeInsightFixtureTestCase() {
  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    myFixture.addClass("package java.awt;" +
                       "public class Color {" +
                       "public Color(int rgb) {}" +
                       "public Color(int r, int g, int b) {}" +
                       "public Color(int rgba, boolean hasalpha) {}" +
                       "public Color(float r, float g, float b) {}" +
                       "public Color(float r, float g, float b, float a) {}" +
                       "}")

    myFixture.addClass("package javax.swing.plaf;" +
                       "import java.awt.Color;" +
                       "public class ColorUIResource extends Color {" +
                       "public ColorUIResource(int rgb) {}" +
                       "public ColorUIResource(int r, int g, int b) {}" +
                       "public ColorUIResource(int rgba, boolean hasalpha) {}" +
                       "public ColorUIResource(float r, float g, float b) {}" +
                       "public ColorUIResource(float r, float g, float b, float a) {}" +
                       "}")
  }

  @Throws(Exception::class)
  fun testColorInFieldInitializer() {
    doTest(1, "Color c = new Color(12, 23, 123);")
  }

  @Throws(Exception::class)
  fun testHex() {
    doTest(1, "Color c = new Color(0x123abc);")
  }

  @Throws(Exception::class)
  fun testColorUIResourceInFieldInitializer() {
    doTest(1, "ColorUIResource c = new ColorUIResource(12, 23, 123);")
  }

  @Throws(Exception::class)
  fun testColorInVariableInitializer() {
    doTest(1, "void foo() {\n" +
              "  Color c = new Color(12, 23, 123);\n" +
              "}")
  }

  @Throws(Exception::class)
  fun testColorInLambda() {
    doTest(1, "void foo() {\n" +
              "  new Runnable() {\n" +
              "    public void run() {\n" +
              "      new Color(12, 23, 123);\n" +
              "    }\n" +
              "  }\n" +
              "}")
  }


  private fun doTest(expectedGuttersCount: Int, code: String) {
    myFixture.configureByText("Foo.java",
                              "import java.awt.Color;\n" +
                              "import javax.swing.plaf.ColorUIResource;\n" +
                              "public class Foo {\n" +
                              code + "\n" +
                              "}")

    val count = myFixture.findAllGutters()
      .filter({ g -> g.icon is ColorIcon })
      .count()

    assertEquals(expectedGuttersCount, count)
  }
}