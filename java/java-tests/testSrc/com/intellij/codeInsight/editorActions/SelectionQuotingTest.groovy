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
package com.intellij.codeInsight.editorActions

import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.testFramework.TestFileType
import com.intellij.codeInsight.CodeInsightSettings

/** 
 * @author Denis Zhdanov
 * @since 7/14/11 2:15 PM 
 */
class SelectionQuotingTest extends AbstractEditorTest {

  void testBuggySelection() {

    def initial = '''\
var a =  "a test";
var b = <selection>[a]</selection><caret>;
var c = "test test test";\
'''
    def action = { type('"abc')}
    
    def expected = '''\
var a =  "a test";
var b = "abc<caret>";
var c = "test test test";\
'''
    
    doTest(initial, expected, action, TestFileType.JS)
  }

  def void doTest(initial, expected, action, fileType = TestFileType.JAVA) {
    configureFromFileText("${getTestName(false)}.$fileType.extension", initial)
    def settings = CodeInsightSettings.getInstance()
    def old = settings.SURROUND_SELECTION_ON_QUOTE_TYPED
    settings.SURROUND_SELECTION_ON_QUOTE_TYPED = true
    try {
      action()
    }
    finally {
      settings.SURROUND_SELECTION_ON_QUOTE_TYPED = old
    }
    checkResultByText(expected)
  }
}
