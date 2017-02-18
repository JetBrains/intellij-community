/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.model;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class ErrorInfo {
  private static final String KEY = "Inspection.Errors";

  private final String myName;
  private final String myPropertyName;
  private final HighlightDisplayLevel myLevel;
  private final List<QuickFix> myQuickFixes = new ArrayList<>();

  public ErrorInfo(@NotNull String name, @Nullable String propertyName, @NotNull HighlightDisplayLevel level) {
    myName = name;
    myPropertyName = propertyName;
    myLevel = level;
  }

  public String getName() {
    return myName;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  @Nullable
  public String getPropertyName() {
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