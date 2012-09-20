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
  
  private def doTest(Map args) {
    configureFromFileText("${getTestName(false)}.java", args.initial)
    executeAction(FixDocCommentAction.ACTION_ID)
    checkResultByText(args.expected)
  }
}
