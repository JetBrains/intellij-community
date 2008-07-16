package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.LanguageExtension;

/**
 * @author spleaner
 */
public class SmartEnterProcessors extends LanguageExtension<SmartEnterProcessor> {

  public static final SmartEnterProcessors INSTANCE = new SmartEnterProcessors();

  public SmartEnterProcessors() {
    super("com.intellij.lang.smartEnterProcessor");
  }
}
