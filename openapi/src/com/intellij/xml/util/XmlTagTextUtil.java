/*
* Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
* Use is subject to license terms.
*/
package com.intellij.xml.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class XmlTagTextUtil {
  private static Map<String, Character> ourCharacterEntities;

  static {
    ourCharacterEntities = new HashMap<String, Character>();
    ourCharacterEntities.put("lt", new Character('<'));
    ourCharacterEntities.put("gt", new Character('>'));
    ourCharacterEntities.put("apos", new Character('\''));
    ourCharacterEntities.put("quot", new Character('\"'));
    ourCharacterEntities.put("amp", new Character('&'));
  }

  private XmlTagTextUtil() {}

  /**
   * if text contains XML-sensitive characters (<,>), quote text with ![CDATA[ ... ]]
   *
   * @param text
   * @return quoted text
   */
  public static String getCDATAQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&\n";
    final int textLength = text.length();
    if(textLength > 0 && (Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(textLength - 1))))
      return "<![CDATA[" + text + "]]>";
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }

  public static String getInlineQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&";
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }


  public static String composeTagText(String tagName, String tagValue) {
    String result = "<" + tagName;
    if (tagValue == null || "".equals(tagValue)) {
      result += "/>";
    }
    else {
      result += ">" + getCDATAQuote(tagValue) + "</" + tagName + ">";
    }
    return result;
  }

  public static String[] getCharacterEntityNames() {
    Set<String> strings = ourCharacterEntities.keySet();
    return strings.toArray(new String[strings.size()]);
  }

  public static Character getCharacterByEntityName(String entityName) {
    return ourCharacterEntities.get(entityName);
  }

}