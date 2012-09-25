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

import org.junit.Test

import static com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType.GETTERS_AND_SETTERS
import static com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType.UTILITY_METHODS
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.PUBLIC
import static com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType.DEPTH_FIRST

/**
 * @author Denis Zhdanov
 * @since 9/18/12 11:19 AM
 */
class JavaRearrangerGrouperTest extends AbstractJavaRearrangerTest {

  void testGettersAndSetters() {
    commonSettings.BLANK_LINES_AROUND_METHOD = 1
    
    doTest(
            initial: '''\
class Test {
  public void setValue(int i) {}
  protected void util() {}
  public int getValue() { return 1; }
}''',
            expected: '''\
class Test {
  public int getValue() { return 1; }

  public void setValue(int i) {}

  protected void util() {}
}''',
      groups: [group(GETTERS_AND_SETTERS)],
      rules: [rule(PUBLIC)]
    )
  }
  
  @Test
  void _testUtilityMethodsDepthFirst() {
    commonSettings.BLANK_LINES_AROUND_METHOD = 0
    doTest(
      initial: '''\
class Test {
  void util1() { util11(); }
  void service1() { util1(); }
  void util2() {}
  void util11() {}
  void service2() { util2(); }
}''',
      groups: [group(UTILITY_METHODS, DEPTH_FIRST)],
      expected: '''\
class Test {
  void service1() { util1(); }
  void util1() { util11(); }
  void util11() {}
  void service2() { util2(); }
  void util2() {}
}'''
    )
  }
}
