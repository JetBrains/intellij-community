/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 22, 2006
 * Time: 5:58:57 PM
 */
package com.intellij.editor;

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
    Document document = myEditor.getDocument();
    document.insertString(myEditor.getCaretModel().getOffset(), STRING6);
    document.insertString(myEditor.getCaretModel().getOffset(), STRING5);
    document.insertString(myEditor.getCaretModel().getOffset(), STRING4);
    document.insertString(myEditor.getCaretModel().getOffset(), STRING3);
    document.insertString(myEditor.getCaretModel().getOffset(), STRING2);
    document.insertString(myEditor.getCaretModel().getOffset(), STRING1);
  }

  private static void doDelete() {
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
}