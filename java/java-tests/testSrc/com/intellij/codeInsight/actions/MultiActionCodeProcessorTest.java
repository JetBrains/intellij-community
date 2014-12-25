/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class MultiActionCodeProcessorTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/actions/codeProcessor/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    myFixture.getFile().putUserData(FormatChangedTextUtil.CHANGED_RANGES, null);
    super.tearDown();
  }

  public void doTest(LayoutCodeOptions options) {
    myFixture.configureByFile(getTestDataPath() + getTestName(true) + "_before.java");
    CodeProcessor processor = new CodeProcessor(myFixture.getFile(), myFixture.getEditor(), options);

    if (options.getTextRangeType() == TextRangeType.VCS_CHANGED_TEXT) {
      CaretModel model = myFixture.getEditor().getCaretModel();
      List<TextRange> ranges = ContainerUtil.mapNotNull(model.getAllCarets(), new Function<Caret, TextRange>() {
        @Override
        public TextRange fun(Caret caret) {
          if (caret.hasSelection()) {
            return new TextRange(caret.getSelectionStart(), caret.getSelectionEnd());
          }
          return null;
        }
      });
      myFixture.getFile().putUserData(FormatChangedTextUtil.CHANGED_RANGES, ranges);
    }

    processor.processCode();
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testSelectionReformat() {
    doTest(new ReformatCodeRunOptions(TextRangeType.SELECTED_TEXT));
  }

  public void testWholeFileReformat() {
    doTest(new ReformatCodeRunOptions(TextRangeType.WHOLE_FILE));
  }

  public void testVcsChangedTextReformat() {
    doTest(new ReformatCodeRunOptions(TextRangeType.VCS_CHANGED_TEXT));
  }


}
