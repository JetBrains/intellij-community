/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME

import org.junit.Before

/**
 * @author Svetlana.Zemlyanskaya
 */

class JavaRearrangerSpecialRuleTest extends AbstractJavaRearrangerTest {

  @Before
  void setUp() {
    super.setUp()
    commonSettings.BLANK_LINES_AROUND_METHOD = 0
    commonSettings.BLANK_LINES_AROUND_CLASS = 0
  }

  void "test name and visibility conditions"() {
    doTest(
      initial: '''\
class Test {
  public void getI() {}
  public void setI() {}
  private void test() {}
}''',
      expected: '''\
class Test {
  public void setI() {}
  public void getI() {}
  private void test() {}
}''',
      rules: [rule(PUBLIC), nameRule("get.*", PUBLIC)]
    )
  }

  void "test modifier conditions"() {
    doTest(
      initial: '''\
class Test {
  public static void a() {}
  public voic b() {}
}
''',
      expected: '''\
class Test {
  public voic b() {}
  public static void a() {}
}
''',
      rules: [rule(PUBLIC), rule(PUBLIC, STATIC)]
    )
  }

  void "test multi modifier condition"() {
    doTest(
      initial: '''\
class Test {
  public abstract void a() {}
  public static void b() {}
  public voic c() {}
}
''',
      expected: '''\
class Test {
  public voic c() {}
  public static void b() {}
  public abstract void a() {}
}
''',
      rules: [rule(PUBLIC), rule(PUBLIC, STATIC), rule(PUBLIC, ABSTRACT)]
    )
  }

  void "test modifier conditions with sort"() {
    doTest(
      initial: '''\
class Test {
  public void e() {}
  public static void d() {}
  public void c() {}
  public static void b() {}
  public void a() {}
}
''',
      expected: '''\
class Test {
  public void a() {}
  public void c() {}
  public void e() {}
  public static void b() {}
  public static void d() {}
}
''',
      rules: [ruleWithOrder(BY_NAME, rule(PUBLIC)), ruleWithOrder(BY_NAME, rule(PUBLIC, STATIC))]
    )
  }

  void "test different entries type with modifier conditions"() {
    doTest(
      initial: '''\
class Test {
  public static void b() {}
  public void a() {}
}
''',
      expected: '''\
class Test {
  public void a() {}
  public static void b() {}
}
''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD), rule(METHOD, PUBLIC), rule(METHOD), rule(METHOD, PUBLIC, ABSTRACT), rule(METHOD, ABSTRACT),
              rule(FIELD, PUBLIC, STATIC), rule(FIELD, STATIC), rule(METHOD, PUBLIC, STATIC), rule(METHOD, STATIC)]
    )
  }
}
