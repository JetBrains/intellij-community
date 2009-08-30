package com.intellij.refactoring.inline;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author yole
 */
public abstract class JavaInlineActionHandler extends InlineActionHandler {
  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l instanceof JavaLanguage ||
           l.equals(StdFileTypes.JSPX.getLanguage()) ||
           l.equals(StdFileTypes.JSP.getLanguage());
  }
}
