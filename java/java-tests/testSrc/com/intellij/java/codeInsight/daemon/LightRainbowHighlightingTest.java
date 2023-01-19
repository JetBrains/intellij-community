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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.UsedColors;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightRainbowHighlightingTest extends LightJavaCodeInsightFixtureTestCase {

  public void testRainbowOff() {
    checkRainbow(
      """
        class TestClass {
            private static int field = 1;
            public static void main(String[] args) {
                ++field;
                String local1 = null;
                System.out.println(field + local1 + args[0]);
            }
        }""", false, false);
  }

  public void testRainbowSameColorsForSameIds() {
    checkRainbow(
      """
        class TestClass {
            public static void main(String[] <rainbow color='ff000003'>args</rainbow>) {
                String <rainbow color='ff000004'>local1</rainbow> = null;
                System.out.println(<rainbow color='ff000004'>local1</rainbow>
                                   + <rainbow color='ff000003'>args</rainbow>[0]);
            }
        }""", true, true);
  }

  public void testRainbowHighlightingIds() {
    // check no coloring for [this] and etc.
    checkRainbow(
      """
        class TestClass {
            private static int SFIELD = 1;
            private static int myField = 1;
            private static Runnable myRunnable = () -> {int <rainbow>a</rainbow> = 0;
                                                        <rainbow>a</rainbow>++;};
            public void f(int <rainbow>param1</rainbow>,
                          int <rainbow>param2</rainbow>,
                          int <rainbow>param3</rainbow>) {
                SFIELD = <rainbow>param1</rainbow> + <rainbow>param2</rainbow> + <rainbow>param3</rainbow> + myField;
                <rainbow>param1</rainbow> = this.hashCode() + this.myField;
            }
        }""", true, false);
  }

  public void testRainbowParamsInJavadocHaveTheSameColorsAsInCode() {
    checkRainbow(
      """
        class TestClass {
            /**
             *\s
             * @param <rainbow color='ff000002'>param1</rainbow>
             * @param <rainbow color='ff000004'>param2</rainbow>
             * @param <rainbow color='ff000001'>param3</rainbow>
             */
            public void f(int <rainbow color='ff000002'>param1</rainbow>,
                          int <rainbow color='ff000004'>param2</rainbow>,
                          int <rainbow color='ff000001'>param3</rainbow>) {
            }
        }""", true, true);
  }

  public void testBadStringHashValue() {
    int color = UsedColors.getOrAddColorIndex(new UserDataHolderBase(), "JHZaWC", 5);
    assertEquals(3, color);
  }

  void checkRainbow(@NotNull String code, boolean isRainbowOn, boolean withColor) {
    myFixture.testRainbow(
      "rainbow.java",
      code,
      isRainbowOn, withColor);
  }
}
