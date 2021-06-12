package com.intellij.ide.starters.shared;

import org.jetbrains.annotations.Nls;

@FunctionalInterface
public interface TextValidationFunction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String checkText(String fieldText);
}