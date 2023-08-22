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
import com.intellij.codeInsight.actions.FileInEditorProcessor;
import com.intellij.codeInsight.actions.LayoutCodeOptions;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.DetectableIndentOptionsProvider;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class JavaDetectableIndentsTest extends BasePlatformTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/detectableIndents/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DetectableIndentOptionsProvider.getInstance().setEnabledInTest(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      DetectableIndentOptionsProvider.getInstance().setEnabledInTest(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSpaceIndent() {
    doTest();
  }

  public void testTabIndent() {
    doTest();
  }

  public void testWithFullReformat() {
    String testName = getTestName(true);
    myFixture.configureByFile(BASE_PATH + testName + ".java");
    CommonCodeStyleSettings javaSettings =
      CodeStyle.getSettings(myFixture.getEditor()).getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.getIndentOptions().INDENT_SIZE = 2;
    javaSettings.getIndentOptions().CONTINUATION_INDENT_SIZE = 2;
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = false;
    LayoutCodeOptions options = new LayoutCodeOptions() {
      @Override
      public TextRangeType getTextRangeType() {
        return TextRangeType.WHOLE_FILE;
      }

      @Override
      public boolean isOptimizeImports() {
        return false;
      }

      @Override
      public boolean isRearrangeCode() {
        return false;
      }
    };
    FileInEditorProcessor processor = new FileInEditorProcessor(myFixture.getFile(), myFixture.getEditor(), options);
    processor.processCode();
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n');
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.java");
  }

  private void doTest() {
    String testName = getTestName(true);
    myFixture.configureByFile(BASE_PATH + testName + ".java");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n');
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }
}
