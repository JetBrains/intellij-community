// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.macro.CapitalizeAndUnderscoreMacro;
import com.intellij.codeInsight.template.macro.ConvertToCamelCaseMacro;
import com.intellij.codeInsight.template.macro.SplitWordsMacro;
import junit.framework.TestCase;

public class NamingConventionMacrosTest extends TestCase {
  public void testCapitalizeAndUnderscore() {
    assertEquals("FOO_BAR", cau("fooBar"));
    assertEquals("FOO_BAR", cau("fooBar"));
    assertEquals("FOO_BAR", cau("foo-Bar"));
    assertEquals("FOO_BAR", cau("foo-bar"));
    assertEquals("", cau(""));
  }

  public void testSnakeCase() {
    assertEquals("foo", snakeCase("foo"));
    assertEquals("foo_bar", snakeCase("foo-bar"));
    assertEquals("foo_bar", snakeCase("fooBar"));
    assertEquals("foo_bar", snakeCase("FOO_BAR"));
    assertEquals("_foo_bar_", snakeCase("-FOO-BAR-"));
    assertEquals("a_b_c_d_e_f_g", snakeCase("a-b.c/d|e*f+g"));
    assertEquals("a_b", snakeCase("a--b"));
    assertEquals("foo_bar", snakeCase("FOO BAR"));
    assertEquals("", snakeCase(""));
  }

  public void testLowercaseAndDash() {
    assertEquals("foo-bar", new SplitWordsMacro.LowercaseAndDash().convertString("FOO_BAR"));
    assertEquals("", new SplitWordsMacro.LowercaseAndDash().convertString(""));
  }

  public void testToCamelCase() {
    assertEquals("fooBar", new ConvertToCamelCaseMacro().convertString("foo-bar").toString());
    assertEquals("fooBar", new ConvertToCamelCaseMacro().convertString("FOO-BAR").toString());
    assertEquals("fooBar", new ConvertToCamelCaseMacro().convertString("foo bar").toString());
    assertEquals("", new ConvertToCamelCaseMacro().convertString("").toString());
  }

  public void testSpaceSeparated() {
    assertEquals("foo Bar", new SplitWordsMacro.SpaceSeparated().convertString("fooBar"));
    assertEquals("foo bar", new SplitWordsMacro.SpaceSeparated().convertString("foo-bar"));
    assertEquals("", new SplitWordsMacro.SpaceSeparated().convertString(""));
  }

  public void testUnderscoresToCamelCase() {
    assertEquals("fooBar-goo", new ConvertToCamelCaseMacro.ReplaceUnderscoresToCamelCaseMacro().convertString("foo_bar-goo").toString());
    assertEquals("", new ConvertToCamelCaseMacro.ReplaceUnderscoresToCamelCaseMacro().convertString("").toString());
  }

  private static String snakeCase(String s) {
    return new SplitWordsMacro.SnakeCaseMacro().convertString(s);
  }

  private static String cau(String s) {
    return new CapitalizeAndUnderscoreMacro().convertString(s);
  }
}
