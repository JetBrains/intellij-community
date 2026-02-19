// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class SeveritiesProvider {
  public static final ExtensionPointName<SeveritiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.severitiesProvider");

  /**
   * @see com.intellij.openapi.editor.colors.TextAttributesKey#createTextAttributesKey(String, TextAttributes)
   */
  public abstract @NotNull List<@NotNull HighlightInfoType> getSeveritiesHighlightInfoTypes();

  public boolean isGotoBySeverityEnabled(HighlightSeverity minSeverity) {
    return minSeverity != HighlightSeverity.INFORMATION;
  }
}
