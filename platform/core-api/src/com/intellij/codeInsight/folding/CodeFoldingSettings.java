package com.intellij.codeInsight.folding;

import com.intellij.openapi.components.ServiceManager;

public class CodeFoldingSettings {
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_IMPORTS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_METHODS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_FILE_HEADER = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_DOC_COMMENTS = false;

  public static CodeFoldingSettings getInstance() {
    return ServiceManager.getService(CodeFoldingSettings.class);
  }
}
