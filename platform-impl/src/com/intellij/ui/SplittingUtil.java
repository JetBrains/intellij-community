package com.intellij.ui;

import java.awt.*;
import java.util.ArrayList;

public class SplittingUtil {
  public static String[] splitText(String text, FontMetrics fontMetrics, int widthLimit, char separator){
    ArrayList lines = new ArrayList();
    String currentLine = "";
    StringBuffer currentAtom = new StringBuffer();

    for (int i=0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      if (ch == separator) {
        currentLine += currentAtom.toString();
        currentAtom.setLength(0);
      }

      String s = currentLine + currentAtom.toString();
      int width = fontMetrics.stringWidth(s);

      if (width >= widthLimit - fontMetrics.charWidth('w')) {
        if (currentLine.length() > 0) {
          lines.add(currentLine);
          currentLine = "";
        }
        else {
          lines.add(currentAtom.toString());
          currentAtom.setLength(0);
        }
      }
    }

    String s = currentLine + currentAtom.toString();
    if (s.length() > 0) {
      lines.add(s);
    }

    return (String[])lines.toArray(new String[lines.size()]);
  }
}
