package com.intellij.codeInspection;

import com.intellij.lang.LanguageExtension;

public class LanguageInspectionSuppressors extends LanguageExtension<InspectionSuppressor> {
  public static final LanguageInspectionSuppressors INSTANCE = new LanguageInspectionSuppressors();

  private LanguageInspectionSuppressors() {
    super("com.intellij.lang.inspectionSuppressor");
  }

}