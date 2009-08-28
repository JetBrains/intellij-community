/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.Annotator;
import org.jetbrains.annotations.NonNls;

public class LanguageAnnotators extends LanguageExtension<Annotator> {
  public static final LanguageAnnotators INSTANCE = new LanguageAnnotators();
  @NonNls public static final String EP_NAME = "com.intellij.annotator";

  private LanguageAnnotators() {
    super(EP_NAME);
  }
}