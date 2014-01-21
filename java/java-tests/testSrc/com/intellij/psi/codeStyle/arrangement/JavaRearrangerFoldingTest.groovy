/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PRIVATE
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC

/**
 * @author Denis Zhdanov
 * @since 11/20/12 3:34 PM
 */
class JavaRearrangerFoldingTest extends AbstractJavaRearrangerTest {
  
  void "test dummy"() {
  }

  void "test that doc comment folding is preserved"() {
    commonSettings.BLANK_LINES_AROUND_METHOD = 1
    doTest(
      initial: '''\
import <fold>java.util.List;
import java.util.Set;</fold>

<fold text="/**...*/">/**
* Class comment
*/</fold>
class Test {

  <fold text="/**...*/>/**
   * Method comment
   */</fold>
  private void test(List<String> l) {}

  <fold text="/**...*/>/**
   * Another method comment
   */</fold>
  public void test(Set<String> s) {}
}''',

      rules: [rule(PUBLIC), rule(PRIVATE)],

      expected: '''\
import <fold>java.util.List;
import java.util.Set;</fold>

<fold text="/**...*/">/**
* Class comment
*/</fold>
class Test {

  <fold text="/**...*/>/**
   * Another method comment
   */</fold>
  public void test(Set<String> s) {}

  <fold text="/**...*/>/**
   * Method comment
   */</fold>
  private void test(List<String> l) {}
}'''
    )
  }

  void "test that doc comment and method folding is preserved"() {
    commonSettings.BLANK_LINES_AROUND_METHOD = 1
    doTest(
      initial: '''\
import java.util.List;
import java.util.Set;

class MyTest {
    <fold text="/**...*/">/**
     * comment 1
     *
     * @param s
     */</fold>
    private void test(String s) {
    }

    /**
     * comment 2
     *
     * @param i
     */
    public void test(int i) {
    }
}''',

      rules: [rule(PUBLIC), rule(PRIVATE)],

      expected: '''\
import java.util.List;
import java.util.Set;

class MyTest {
    /**
     * comment 2
     *
     * @param i
     */
    public void test(int i) {
    }

    <fold text="/**...*/">/**
     * comment 1
     *
     * @param s
     */</fold>
    private void test(String s) {
    }
}'''
    )
  }

  void "test that single doc comment folding is preserved"() {
    commonSettings.BLANK_LINES_AROUND_METHOD = 1
    doTest(
      initial: '''\
package a.b;

class MyTest {
    /**
     * private comment
     *
     * @param s
     */
    private void test(String s) {
    }

    /**
     * comment 2
     *
     * @param i
     */
    public void test(int i) <fold text="{...}">{
        System.out.println(1);
    }</fold>
}''',

      rules: [rule(PUBLIC), rule(PRIVATE)],

      expected: '''\
package a.b;

class MyTest {
    /**
     * comment 2
     *
     * @param i
     */
    public void test(int i) <fold text="{...}">{
        System.out.println(1);
    }</fold>

    /**
     * private comment
     *
     * @param s
     */
    private void test(String s) {
    }
}'''
    )
  }
}
