/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import org.jetbrains.annotations.NotNull;

public class CompletionCharFilter extends CharFilter {

  public Result acceptChar(char c, @NotNull final String prefix, final Lookup lookup) {
    if (!lookup.isCompletion()) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch(c){
      case '.':
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '!':
      case '(':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return Result.HIDE_LOOKUP;
    }
  }

}