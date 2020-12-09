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
package com.intellij.java.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

/**
 * @author Rustam Vishnyakov
 */
public class JavaEnterInStringLiteralTest extends LightJavaCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/stringLiteral/";

  public void testNonIndentedTextBlockContent() {
    doTest();
  }

  public void testContentAtTheStartOfTextBlock() {
    doTest();
  }

  public void testOnlyWhitespacesTextBlock() {
    doTest();
  }

  public void testContentAtTheEndOfTextBlock() {
    doTest();
  }

  public void testEmptyTextBlock() {
    doTest();
  }

  public void testEnter() {
    doTest();
  }

  public void testEnterOpSignOnNextLine() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean opSignOnNextLine = settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE;
    try {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE =  true;
      doTest();
    }
    finally {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = opSignOnNextLine;
    }
  }

  private void doTest() {
    String testName = getTestName(true);
    configureByFile(BASE_PATH + testName + ".java");
    EditorTestUtil.performTypingAction(getEditor(), '\n');
    checkResultByFile(BASE_PATH + testName + "_after.java");
  }
}
