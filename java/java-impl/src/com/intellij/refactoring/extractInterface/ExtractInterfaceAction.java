package com.intellij.refactoring.extractInterface;

import com.intellij.refactoring.actions.BaseExtractModuleAction;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;

/**
 * @author Dennis.Ushakov
 */
public class ExtractInterfaceAction extends BaseExtractModuleAction {

  @Override
  protected boolean isEnabledOnLanguage(Language language) {
    return language instanceof JavaLanguage;
  }
}
