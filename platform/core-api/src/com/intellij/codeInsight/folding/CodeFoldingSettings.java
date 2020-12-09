// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.folding;

import com.intellij.openapi.application.ApplicationManager;

public class CodeFoldingSettings {
  public boolean COLLAPSE_IMPORTS = true;
  public boolean COLLAPSE_METHODS;
  public boolean COLLAPSE_FILE_HEADER = true;
  public boolean COLLAPSE_DOC_COMMENTS;
  public boolean COLLAPSE_CUSTOM_FOLDING_REGIONS;

  public static CodeFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getService(CodeFoldingSettings.class);
  }
}
