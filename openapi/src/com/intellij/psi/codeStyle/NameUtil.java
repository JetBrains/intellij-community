/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

import java.util.ArrayList;

public class NameUtil {
  public static String[] nameToWords(String name){
    ArrayList array = new ArrayList();
    int index = 0;
    int wordStart;
  WordsLoop:
    while(index < name.length()){
      wordStart = index;
      int upperCaseCount = 0;
      int lowerCaseCount = 0;
      int digitCount = 0;
      int specialCount = 0;
      while(index < name.length()){
        char c = name.charAt(index);
        if (Character.isDigit(c)){
          if (upperCaseCount > 0 || lowerCaseCount > 0 || specialCount > 0) break;
          digitCount++;
        }
        else if (Character.isUpperCase(c)){
          if (lowerCaseCount > 0 || digitCount > 0 || specialCount > 0) break;
          upperCaseCount++;
        }
        else if (Character.isLowerCase(c)){
          if (digitCount > 0 || specialCount > 0) break;
          if (upperCaseCount > 1) {
            index--;
            break;
          }
          lowerCaseCount++;
        }
        else{
          if (upperCaseCount > 0 || lowerCaseCount > 0 || digitCount > 0) break;
          specialCount++;
        }
        index++;
      }
      String word = name.substring(wordStart, index);
      array.add(word);
    }
    return (String[])array.toArray(new String[array.size()]);
  }
}