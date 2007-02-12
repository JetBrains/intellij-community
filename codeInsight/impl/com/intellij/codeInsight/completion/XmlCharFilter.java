/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:15:07 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;

public class XmlCharFilter implements CharFilter {
  private boolean myWithinTag;

  public XmlCharFilter(boolean withinTag) {
    myWithinTag = withinTag;
  }

  public int accept(char c, final String prefix) {
    if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
    switch(c){
      case ':':
      case '.':
      case '-':
        return CharFilter.ADD_TO_PREFIX;
      case ',':
      case ';':
      case '=':
      case '(':

      case '/':
        return myWithinTag && prefix != null && prefix.length() > 0 ? CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP:CharFilter.ADD_TO_PREFIX;
        
      case '>': if (prefix != null && prefix.length() > 0) {
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
      }

      default:
        return CharFilter.HIDE_LOOKUP;
      case ' ':
        return SELECT_ITEM_AND_FINISH_LOOKUP;
    }
  }
}
