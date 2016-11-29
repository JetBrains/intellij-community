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
package com.intellij.codeInsight.template

import com.intellij.codeInsight.template.macro.ConvertToCamelCaseMacro
import com.intellij.codeInsight.template.macro.SplitWordsMacro
import com.intellij.psi.codeStyle.NameUtil
import junit.framework.TestCase
/**
 * @author peter
 */
class NamingConventionMacrosTest extends TestCase {

  void "test capitalize and underscore"() {
    assert "FOO_BAR" == cau("fooBar")
    assert "FOO_BAR" == cau("fooBar")
    assert "FOO_BAR" == cau("foo-Bar")
    assert "FOO_BAR" == cau("foo-bar")
    assert "" == cau("")
  }

  void "test snake case"() {
    assert "foo" == snakeCase("foo")
    assert "foo_bar" == snakeCase("foo-bar")
    assert "foo_bar" == snakeCase("fooBar")
    assert "foo_bar" == snakeCase("FOO_BAR")
    assert "_foo_bar_" == snakeCase("-FOO-BAR-")
    assert "a_b_c_d_e_f_g" == snakeCase("a-b.c/d|e*f+g")
    assert "a_b" == snakeCase("a--b")
    assert "foo_bar" == snakeCase("FOO BAR")
    assert "" == snakeCase("")
  }

  void "test lowercase and dash"() {
    assert "foo-bar" == new SplitWordsMacro.LowercaseAndDash().convertString("FOO_BAR")
    assert "" == new SplitWordsMacro.LowercaseAndDash().convertString("")
  }

  void "test to camel case"() {
    assert "fooBar" == new ConvertToCamelCaseMacro().convertString("foo-bar")?.toString()
    assert "fooBar" == new ConvertToCamelCaseMacro().convertString("FOO-BAR")?.toString()
    assert "fooBar" == new ConvertToCamelCaseMacro().convertString("foo bar")?.toString()
    assert "" == new ConvertToCamelCaseMacro().convertString("")?.toString()
  }

  void "test space separated"() {
    assert "foo Bar" == new SplitWordsMacro.SpaceSeparated().convertString("fooBar")
    assert "foo bar" == new SplitWordsMacro.SpaceSeparated().convertString("foo-bar")
    assert "" == new SplitWordsMacro.SpaceSeparated().convertString("")
  }

  void "test underscoresToCamelCase"() {
    assert "fooBar-goo" == new ConvertToCamelCaseMacro.ReplaceUnderscoresToCamelCaseMacro().convertString("foo_bar-goo")?.toString()
    assert "" == new ConvertToCamelCaseMacro.ReplaceUnderscoresToCamelCaseMacro().convertString("")?.toString()
  }

  private static def snakeCase(String s) {
    return new SplitWordsMacro.SnakeCaseMacro().convertString(s)
  }
  private static def cau(String s) {
    return NameUtil.capitalizeAndUnderscore(s)
  }
  
}
