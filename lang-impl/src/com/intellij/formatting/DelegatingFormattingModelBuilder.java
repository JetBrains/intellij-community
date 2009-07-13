package com.intellij.formatting;

public interface DelegatingFormattingModelBuilder extends FormattingModelBuilder {
  boolean dontFormatMyModel();
}
