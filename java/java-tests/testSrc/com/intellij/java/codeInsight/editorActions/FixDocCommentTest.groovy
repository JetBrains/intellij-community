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
package com.intellij.java.codeInsight.editorActions

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.FixDocCommentAction
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.psi.codeStyle.JavaCodeStyleBean
import org.jetbrains.annotations.NotNull

/**
 * @author Denis Zhdanov
 * @since 9/20/12 6:17 PM
 */
class FixDocCommentTest extends AbstractEditorTest {

  void testGenerateMethodDoc() {
    doTest(
      initial: '''\
class Test {
    String test(int i) {
        return "s";<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i <caret>
     * @return
     */
    String test(int i) {
        return "s";
    }
}'''
    )
  }

  void testGenerateFieldDoc() {
    doTest(
      initial: '''\
class Test {
    int <caret>i;
}''',
      expected: '''\
class Test {
    /**
     * <caret>
     */
    int i;
}'''
    )
  }

  void testGenerateClassDoc() {
    doTest(
      initial: '''\
class Test {
    void test1() {}
<caret>
    void test2() {}
}''',
      expected: '''\
/**
 * <caret>
 */
class Test {
    void test1() {}

    void test2() {}
}'''
    )
  }

  void testRemoveOneParameterFromMany() {
    doTest(
      initial: '''\
class Test {
    /**
     * @param i
     * @param j
     * @param k
     */
    void test(int i, int j) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i <caret>
     * @param j
     */
    void test(int i, int j) {
    }
}'''
    )
  }

  void testRemoveTheOnlyParameter() {
    doTest(
      initial: '''\
class Test {
    /**
     * My description
     * @param i
     */
    void test() {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * My description
     */
    void test() {<caret>
    }
}'''
    )
  }

  void testRemoveReturn() {
    doTest(
      initial: '''\
class Test {
    /**
     * My description
     * @return data
     */
    void test() {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * My description
     */
    void test() {<caret>
    }
}'''
    )
  }

  void testRemoveOneThrowsFromMany() {
    doTest(
      initial: '''\
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
    /**
     * @param i  my arg
     * @throws MyException1  text1
     * @throws MyException2  text2
     */
    void test(int i) throws MyException2 {<caret>
    }
}''',
      expected: '''\
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
    /**
     * @param i  my arg
     * @throws MyException2  text2
     */
    void test(int i) throws MyException2 {
    }
}'''
    )
  }

  void testRemoveTheOnlyThrows() {
    doTest(
      initial: '''\
class MyException extends Exception {}

class Test {
    /**
     * @param i  my arg
     * @throws MyException  text
     */
    void test(int i) {<caret>
    }
}''',
      expected: '''\
class MyException extends Exception {}

class Test {
    /**
     * @param i  my arg
     */
    void test(int i) {
    }
}'''
    )
  }

  void testRemoveOneTypeParameterFromMany() {
    doTest(
      initial: '''\
/**
 * @param <T> tDescription
 * @param <V> vDescription
 */
class Test<V> {<caret>
}''',
      expected: '''\
/**
 * @param <V> vDescription
 */
class Test<V> {<caret>
}'''
    )
  }

  void testRemoveMultipleTypeParameter() {
    doTest(
      initial: '''\
/**
 * @param <T> tDescription
 * @param <V> vDescription
 */
class Test {<caret>
}''',
      expected: '''\
/**
 */
class Test {<caret>
}'''
    )
  }

  void testAddFirstParameter() {
    doTest(
      initial: '''\
class Test {
    /**
     */ 
    void test(int i) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i <caret>
     */ 
    void test(int i) {
    }
}'''
    )
  }

  void testAddMultipleParameter() {
    doTest(
      initial: '''\
class Test {
    /**
     * @param i
     */ 
    void test(int i, int j, int k) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i <caret>
     * @param j
     * @param k
     */ 
    void test(int i, int j, int k) {
    }
}'''
    )
  }

  void testAddReturn() {
    doTest(
      initial: '''\
class Test {
    /**
     */ 
    int test() {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @return<caret>
     */ 
    int test() {
    }
}'''
    )
  }

  void testAddFirstThrows() {
    doTest(
      initial: '''\
class MyException extends Exception {}

class Test {
    /**
     * @param i  my arg
     */
    void test(int i) throws MyException {<caret>
    }
}''',
      expected: '''\
class MyException extends Exception {}

class Test {
    /**
     * @param i  my arg
     * @throws MyException<caret>
     */
    void test(int i) throws MyException {
    }
}''')
  }

  void testAddNonFirstThrows() {
    doTest(
      initial: '''\
class MyException1 extends Exception {}
class MyException2 extends Exception {}
class MyException3 extends Exception {}

class Test {
    /**
     * @param i  my arg
     * @throws MyException1
     */
    void test(int i) throws MyException1, MyException2, MyException3 {<caret>
    }
}''',
      expected: '''\
class MyException1 extends Exception {}
class MyException2 extends Exception {}
class MyException3 extends Exception {}

class Test {
    /**
     * @param i  my arg
     * @throws MyException1<caret>
     * @throws MyException2
     * @throws MyException3
     */
    void test(int i) throws MyException1, MyException2, MyException3 {
    }
}''')
  }

  void testAddFirstThrowsWhenEmptyReturnIsAvailable() {
    doTest(
      initial: '''\
class MyException extends Exception {}

class Test {
    /**
     * @return
     */
    int test() throws MyException {<caret>
        return 1;
    }
}''',
      expected: '''\
class MyException extends Exception {}

class Test {
    /**
     * @return<caret>
     * @throws MyException
     */
    int test() throws MyException {
        return 1;
    }
}''')
  }
  
  void testAddFirstTypeParameter() {
    doTest(
      initial: '''\
/**
 * My description
 * @author me
 */
class Test<T> {<caret>
}''',
      expected: '''\
/**
 * My description
 * @author me
 * @param <T> <caret>
 */
class Test<T> {
}''')
  }

  void testAddNonFirstTypeParameter() {
    doTest(
      initial: '''\
/**
 * My description
 * @author me
 * @param <T>    type description<caret>
 */
class Test<T, V> {
}''',
      expected: '''\
/**
 * My description
 * @author me
 * @param <T>    type description
 * @param <V> <caret>
 */
class Test<T, V> {
}''')
  }

  void testCorrectParametersOrder() {
    doTest(
      initial: '''\
class Test {
    /**
     * @param j
     * @param k    single line description
     * @param i    multi-line
     *             description
     */
    public void test(int i, int j, int k) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i    multi-line
     *             description
     * @param j    <caret>
     * @param k    single line description
     */
    public void test(int i, int j, int k) {
    }
}'''
    )
  }

  void testCorrectParametersDescriptionWhenIndentIsDefines() {
    doTest(
      initial: '''\
class Test {
    /**
     * @param j    
     * @param i
     */
    public void test(int i, int j) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i <caret>
     * @param j    
     */
    public void test(int i, int j) {
    }
}'''
    )
  }

  void testCorrectMethodTypeParametersOrder() {
    doTest(
      initial: '''\
class Test {
  /**
   * @param <B>
   * @param <A>    A description
   */
  <A, B> void test() {<caret>
  }
}''',
      expected: '''\
class Test {
  /**
   * @param <A>    A description
   * @param <B> <caret>
   */
  <A, B> void test() {
  }
}'''
    )
  }

  void testCorrectClassTypeParametersOrder() {
    doTest(
      initial: '''\
/**
 * Class description
 * @author Zigmund
 * @param <B>    multi-line
 *               description
 * @param <A>
 */
class Test<A, B> {<caret>
}''',
      expected: '''\
/**
 * Class description
 * @author Zigmund
 * @param <A> <caret>
 * @param <B>    multi-line
 *               description
 */
class Test<A, B> {
}'''
    )
  }

  void testAllesZusammen() {
    doTest(
      initial: '''\
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
    /**
     * Method description
     * @param j    j description (single line)
     * @param s    s description
     * @param k
     *             k description (single line but located at another line)
     * @throws MyException2
     * @return some value
     */
    void test(int i, int j, int k) throws MyException1 {<caret>
    }
}''',
      expected: '''\
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
    /**
     * Method description
     * @param i    <caret>
     * @param j    j description (single line)
     * @param k
     *             k description (single line but located at another line)
     * @throws MyException1
     */
    void test(int i, int j, int k) throws MyException1 {
    }
}'''
    )
  }
  
  void testNavigateToMissingParamDescription() {
    doTest(
      initial: '''\
class Test {
    /**
     * @param i
     */ 
    void test(int i) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i <caret>
     */ 
    void test(int i) {
    }
}'''
    )
  }

  void testWithEmptyTagsRemovalOption() {
    codeStyleBean.with {
      javaDocKeepEmptyParameter = false
      javaDocKeepEmptyReturn = false
      javaDocKeepEmptyException = false
    }
    doTest(
      initial: '''package com.company;

public class Test
{
    int foo<caret>(String s, int i, double d) throws Exception
    {
        return 0;
    }
}
''',
      expected: '''package com.company;

public class Test
{
    /**
     * @param s <caret>
     * @param i
     * @param d
     * @return
     * @throws Exception
     */
    int foo(String s, int i, double d) throws Exception
    {
        return 0;
    }
}
'''
    )
  }

  private def doTest(Map args) {
    configureFromFileText("${getTestName(false)}.java", args.initial)
    myEditor.settings.virtualSpace = false
    executeAction(FixDocCommentAction.ACTION_ID)
    checkResultByText(args.expected)
  }

  @NotNull
  static JavaCodeStyleBean getCodeStyleBean() {
    JavaCodeStyleBean codeStyleBean = new JavaCodeStyleBean()
    codeStyleBean.setRootSettings(CodeStyle.getSettings(getProject()))
    return codeStyleBean
  }
}
