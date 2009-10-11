/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
