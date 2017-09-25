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

package com.intellij.java.editor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;

public class LineSetIncrementalUpdateTest extends LightCodeInsightTestCase {
  @NonNls private static final String STRING1 = "\naaa\n";
  @NonNls private static final String STRING2 = "\n  \n";
  @NonNls private static final String STRING3 = "  \n  \n  ";
  @NonNls private static final String STRING4 = "\n  ";
  @NonNls private static final String STRING5 = "  \n";
  @NonNls private static final String STRING6 = "\n";

  public void testInsert() {
    LineSet.setTestingMode(true);
    try {
      configureFromFileText("test.jsp","<caret>");
      doInsert();

      configureFromFileText("test.jsp","aaa\nb<caret>bb\n");
      doInsert();
    }
    finally {
      LineSet.setTestingMode(false);
    }
  }

  public void testDelete() {

    try {
      configureFromFileText("test.jsp","aaa\n<caret>bbb\n");
      doInsert();
      LineSet.setTestingMode(true);

      doDelete();
      LineSet.setTestingMode(false);

      configureFromFileText("test.jsp","<caret>");
      doInsert();
      LineSet.setTestingMode(true);
      doDelete();
      LineSet.setTestingMode(false);
    }
    finally {
      LineSet.setTestingMode(false);
    }
  }

  private static void doInsert() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        Document document = myEditor.getDocument();
        document.insertString(myEditor.getCaretModel().getOffset(), STRING6);
        document.insertString(myEditor.getCaretModel().getOffset(), STRING5);
        document.insertString(myEditor.getCaretModel().getOffset(), STRING4);
        document.insertString(myEditor.getCaretModel().getOffset(), STRING3);
        document.insertString(myEditor.getCaretModel().getOffset(), STRING2);
        document.insertString(myEditor.getCaretModel().getOffset(), STRING1);
      }
    }.execute().throwException();
  }

  private static void doDelete() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        Document document = myEditor.getDocument();

        document.deleteString(
          myEditor.getCaretModel().getOffset(),
          myEditor.getCaretModel().getOffset() + STRING1.length()
        );

        document.deleteString(
          myEditor.getCaretModel().getOffset(),
          myEditor.getCaretModel().getOffset() + STRING2.length()
        );

        document.deleteString(
          myEditor.getCaretModel().getOffset(),
          myEditor.getCaretModel().getOffset() + STRING3.length()
        );

        document.deleteString(
          myEditor.getCaretModel().getOffset(),
          myEditor.getCaretModel().getOffset() + STRING4.length()
        );

        document.deleteString(
          myEditor.getCaretModel().getOffset(),
          myEditor.getCaretModel().getOffset() + STRING5.length()
        );

        document.deleteString(
          myEditor.getCaretModel().getOffset(),
          myEditor.getCaretModel().getOffset() + STRING6.length()
        );
      }
    }.execute().throwException();
  }

  public void testTypingInLongLinePerformance() {
    String longLine = StringUtil.repeat("a ", 200000);
    PlatformTestUtil.startPerformanceTest("Document changes in a long line", 1000, () -> {
      Document document = EditorFactory.getInstance().createDocument("a\n" + longLine + "<caret>" + longLine + "\n");
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        for (int i = 0; i < 1000; i++) {
          int offset = i * 2 + longLine.length();
          assertEquals(1, document.getLineNumber(offset));
          document.insertString(offset, "b");
        }
      });
    }).assertTiming();
  }
}