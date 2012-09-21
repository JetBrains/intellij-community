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
package com.intellij.codeInsight.editorActions

import com.intellij.openapi.editor.impl.AbstractEditorProcessingOnDocumentModificationTest

/**
 * @author Denis Zhdanov
 * @since 9/20/12 6:17 PM
 */
class FixDocCommentTest extends AbstractEditorProcessingOnDocumentModificationTest {

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
     * <caret>
     * @param i
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
     * @param i    <caret>
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
     * @param i    <caret>
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
     * @param i    <caret>
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
 * @param <T>    <caret>
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
 * @param <V>    <caret>
 */
class Test<T, V> {
}''')
  }

  void _testCorrectParametersOrder() {
    doTest(
      initial: '''\
class Test {
    /**
     * @param j
     * @param k    k description
     * @param i
     */
    public void test(int i, int j, int k) {<caret>
    }
}''',
      expected: '''\
class Test {
    /**
     * @param i    <caret>
     * @param j
     * @param k    k description
     */
    public void test(int i, int j, int k) {
    }
}'''
    )
  }

  void testCorrectTypeParametersOrder() {
    // TODO den implement
  }

  void testAllesZusammen() {
    // TODO den implement
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
     * @param i    <caret>
     */ 
    void test(int i) {
    }
}'''
    )
  }

  void testNavigateToMissingReturnDescription() {
    // TODO den implement
  }
  
  void testNavigateToMissingThrowsDescription() {
    // TODO den implement
  }

  private def doTest(Map args) {
    configureFromFileText("${getTestName(false)}.java", args.initial)
    myEditor.settings.virtualSpace = false
    executeAction(FixDocCommentAction.ACTION_ID)
    checkResultByText(args.expected)
  }
}
