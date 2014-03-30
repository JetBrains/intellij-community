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

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*

/**
 * @author Denis Zhdanov
 * @since 8/30/12 12:14 PM
 */
public class JavaRearrangerBlankLinesTest extends AbstractJavaRearrangerTest {

  void testPreserveRelativeBlankLines() {
    commonSettings.BLANK_LINES_AROUND_CLASS = 2
    commonSettings.BLANK_LINES_AROUND_FIELD = 1
    commonSettings.BLANK_LINES_AROUND_METHOD = 2
    commonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 2
    commonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 3
    doTest(
      initial: '''\
class Test {
  private void method1() {}

  public void method2() {}

  private int i;

  public int j;
  public static int k;
}
interface MyInterface {
  void test1();
  void test2();
  int i = 0;
  int j = 0;
}''',
      expected: '''\
interface MyInterface {
  int i = 0;


  int j = 0;



  void test1();



  void test2();
}


class Test {
  public static int k;

  public int j;

  private int i;


  public void method2() {}


  private void method1() {}
}''',
      rules: classic
    )
  }

  void testCutBlankLines() {
    commonSettings.BLANK_LINES_AROUND_FIELD = 0
    commonSettings.BLANK_LINES_AROUND_METHOD = 1
    doTest(
      initial: '''\
class Test {

    void test1() {
    }
    
    void test2() {
    }
    
    int i;
    int j;
}''',
      expected: '''\
class Test {

    int i;
    int j;
    
    void test1() {
    }

    void test2() {
    }
}''',
      rules: [rule(FIELD, PACKAGE_PRIVATE), rule(METHOD)]
    )
  }
  
  void "test blank lines settings are not applied to anonymous classes"() {
    commonSettings.BLANK_LINES_AROUND_CLASS = 1
    def text = '''\
class Test {
  void test() {
    a(new Intf() {});
    a(new Intf() {});
  }
}'''
    doTest(initial: text, expected: text, rules: [rule(CLASS)] )
  }

  void "test statements on the same line"() {
    def before = '''\



public enum Sender {a, b; private String value;
}
'''
    doTest(initial: before, expected: before)
  }



  void "test keep blank lines between fields"() {
    def text = '''\
public class Test {
  private static final Logger LOGGER = LoggerFactory.getLogger(AddCurrentUser.class);


  private GlobalQueryService globalQueryService;
  private EventCoordinationService eventCoordinationService;
}
'''
    doTest(
      initial: text,
      expected: text,
      rules: classic
    )
  }

  void "test keep blank lines between fields more fair test"() {
    doTest(
      initial: '''\
public class Test {
    private static final int t = 12;


    public int q = 2;
    private int e = 3;
    public int t11 = 23;

    private void test() {
    }

    public void main() {
    }

}
''',
      expected: '''\
public class Test {
    private static final int t = 12;


    public int q = 2;
    public int t11 = 23;
    private int e = 3;

    public void main() {
    }

    private void test() {
    }

}
''',
      rules: classic
    )
  }


}
