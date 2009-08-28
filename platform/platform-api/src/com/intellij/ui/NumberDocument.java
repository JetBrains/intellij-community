package com.intellij.ui;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;

public class NumberDocument extends PlainDocument {
  public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
    char[] source = str.toCharArray();
    char[] result = new char[source.length];
    int j = 0;

    for (int i = 0; i < result.length; i++) {
      if (Character.isDigit(source[i])) {
        result[j++] = source[i];
      }
      else {
        Toolkit.getDefaultToolkit().beep();
      }
    }
    super.insertString(offs, new String(result, 0, j), a);
  }
}
