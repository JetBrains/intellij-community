/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 22, 2006
 * Time: 5:58:57 PM
 */
package com.intellij.editor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

public class LineSetIncrementalUpdateTest extends LightCodeInsightTestCase {
  @NonNls private static final String STRING1 = "\naaa\n";
  @NonNls private static final String STRING2 = "\n  \n";
  @NonNls private static final String STRING3 = "  \n  \n  ";
  @NonNls private static final String STRING4 = "\n  ";
  @NonNls private static final String STRING5 = "  \n";
  @NonNls private static final String STRING6 = "\n";

  public void testInsert() throws Exception {
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

  public void testDelete() throws Exception {

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
      protected void run() throws Throwable {
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
      protected void run() throws Throwable {
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
}