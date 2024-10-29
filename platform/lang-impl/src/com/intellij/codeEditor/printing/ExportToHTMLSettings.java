// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor.printing;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "ExportToHTMLSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ExportToHTMLSettings implements PersistentStateComponent<ExportToHTMLSettings> {
  public boolean PRINT_LINE_NUMBERS;
  public boolean OPEN_IN_BROWSER;
  public @NonNls String OUTPUT_DIRECTORY;

  private int myPrintScope;

  private boolean isIncludeSubpackages = false;

  public static ExportToHTMLSettings getInstance(Project project) {
    return project.getService(ExportToHTMLSettings.class);
  }

  public int getPrintScope() {
    return myPrintScope;
  }

  public void setPrintScope(int printScope) {
    myPrintScope = printScope;
  }

  public boolean isIncludeSubdirectories() {
    return isIncludeSubpackages;
  }

  public void setIncludeSubpackages(boolean includeSubpackages) {
    isIncludeSubpackages = includeSubpackages;
  }


  @Override
  public ExportToHTMLSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ExportToHTMLSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
