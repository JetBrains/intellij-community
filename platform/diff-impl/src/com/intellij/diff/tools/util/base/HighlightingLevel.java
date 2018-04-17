// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.base;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public enum HighlightingLevel {
  INSPECTIONS("Inspections", AllIcons.Ide.HectorOn, rangeHighlighter -> {
    return true;
  }),

  ADVANCED("Syntax", AllIcons.Ide.HectorSyntax, rangeHighlighter -> {
    if (rangeHighlighter.getLayer() > HighlighterLayer.ADDITIONAL_SYNTAX) return false;
    HighlightInfo info = HighlightInfo.fromRangeHighlighter(rangeHighlighter);
    if (info != null && info.getSeverity().compareTo(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) >= 0) return false;
    return true;
  }),

  SIMPLE("None", AllIcons.Ide.HectorOff, rangeHighlighter -> {
    return rangeHighlighter.getLayer() <= HighlighterLayer.SYNTAX;
  });

  @NotNull private final String myText;
  @Nullable private final Icon myIcon;
  @NotNull private final Condition<RangeHighlighter> myCondition;

  HighlightingLevel(@NotNull String text, @Nullable Icon icon, @NotNull Condition<RangeHighlighter> condition) {
    myText = text;
    myIcon = icon;
    myCondition = condition;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public Condition<RangeHighlighter> getCondition() {
    return myCondition;
  }
}
