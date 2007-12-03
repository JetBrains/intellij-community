package com.intellij.xml.util;

import org.jetbrains.annotations.NonNls;

public class XmlTagUtilBase {
  public static String escapeString(final String str, final boolean escapeWhiteSpace) {
    if (str == null) return null;
    StringBuffer buffer = null;
    for (int i = 0; i < str.length(); i++) {
      @NonNls String entity;
      char ch = str.charAt(i);
      switch (ch) {
        case '\n':
          entity = escapeWhiteSpace ? "&#10;" : null;
          break;
        case '\r':
          entity = escapeWhiteSpace ? "&#13;" : null;
          break;
        case '\t':
          entity = escapeWhiteSpace ? "&#9;" : null;
          break;
        case'\"':
          entity = "&quot;";
          break;
        case'<':
          entity = "&lt;";
          break;
        case'>':
          entity = "&gt;";
          break;
        case'&':
          entity = "&amp;";
          break;
        case 160: // unicode char for &nbsp;
          entity = "&nbsp;";
          break;
        default:
          entity = null;
          break;
      }
      if (buffer == null) {
        if (entity != null) {
          // An entity occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuffer(str.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(str.substring(0, i));
          buffer.append(entity);
        }
      }
      else {
        if (entity == null) {
          buffer.append(ch);
        }
        else {
          buffer.append(entity);
        }
      }
    }

    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? str : buffer.toString();
  }
}