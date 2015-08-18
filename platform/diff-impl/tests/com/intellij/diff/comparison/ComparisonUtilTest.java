/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison;

import com.intellij.testFramework.UsefulTestCase;

public class ComparisonUtilTest extends UsefulTestCase {
  private static final ComparisonManager manager = new ComparisonManagerImpl();

  public void testTrimEquals() {
    assertTrue(manager.isEquals("", "", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("", "   ", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("   ", "   ", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("\n   ", "  \n", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("asd ", "asd  ", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("    asd", "asd", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("\n\n\n", "\n\n\n", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("\n  \n  \n ", "  \n \n\n  ", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("\n\n", "\n\n\n", ComparisonPolicy.TRIM_WHITESPACES));

    assertFalse(manager.isEquals("\nasd ", "asd\n  ", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("\nasd \n", "\n asd\n  ", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("x", "y", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("\n", " ", ComparisonPolicy.TRIM_WHITESPACES));

    assertTrue(manager.isEquals("\t ", "", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("", "\t\n \n\t", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("\t", "\n", ComparisonPolicy.TRIM_WHITESPACES));

    assertTrue(manager.isEquals("x", " x", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("x", "x ", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("x\n", "x", ComparisonPolicy.TRIM_WHITESPACES));

    assertFalse(manager.isEquals("abc", "a\nb\nc\n", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("\nx y x\n", "\nx y x\n", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("\nxyx\n", "\nx y x\n", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("\nx y x", "\nx y x", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("\nxyx", "\nx y x", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("x y x", "x y x", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("xyx", "x y x", ComparisonPolicy.TRIM_WHITESPACES));
    assertTrue(manager.isEquals("  x y x  ", "x y x", ComparisonPolicy.TRIM_WHITESPACES));

    assertFalse(manager.isEquals("x", "\t\n ", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("", " x ", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("", "x ", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("", " x", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("xyx", "xxx", ComparisonPolicy.TRIM_WHITESPACES));
    assertFalse(manager.isEquals("xyx", "xYx", ComparisonPolicy.TRIM_WHITESPACES));
  }
}
