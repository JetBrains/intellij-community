// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.model;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class ErrorInfo {
  private static final String KEY = "Inspection.Errors";

  private final @Nls String myName;
  private final String myPropertyName;
  private final HighlightDisplayLevel myLevel;
  private final List<QuickFix> myQuickFixes = new ArrayList<>();

  public ErrorInfo(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name, @Nullable String propertyName, @NotNull HighlightDisplayLevel level) {
    myName = name;
    myPropertyName = propertyName;
    myLevel = level;
  }

  public @Nls(capitalization = Nls.Capitalization.Sentence) String getName() {
    return myName;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public @Nullable String getPropertyName() {
    return myPropertyName;
  }

  public List<QuickFix> getQuickFixes() {
    return myQuickFixes;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static boolean haveFixes(List<ErrorInfo> errorInfos) {
    for (ErrorInfo errorInfo : errorInfos) {
      if (!errorInfo.getQuickFixes().isEmpty()) {
        return true;
      }
    }
    return false;
  }
}