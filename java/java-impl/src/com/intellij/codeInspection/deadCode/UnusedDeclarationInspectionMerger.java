// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class UnusedDeclarationInspectionMerger extends InspectionElementsMergerBase {
  private static final String UNUSED_SYMBOL = "UNUSED_SYMBOL";
  private static final String UNUSED_DECLARATION = "UnusedDeclaration";

  @Override
  public @NotNull String getMergedToolName() {
    return UnusedDeclarationInspectionBase.SHORT_NAME;
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {UNUSED_SYMBOL, UNUSED_DECLARATION};
  }

  @Override
  protected Element writeOldSettings(@NotNull String sourceToolName) throws WriteExternalException {
    Element sourceElement = super.writeOldSettings(sourceToolName);
    if (UNUSED_SYMBOL.equals(sourceToolName)) {
      new UnusedSymbolLocalInspection().writeSettings(sourceElement);
    }
    else if (UNUSED_DECLARATION.equals(sourceToolName)) {
      new UnusedDeclarationInspection().writeUnusedDeclarationSettings(sourceElement);
    }
    return sourceElement;
  }
}
