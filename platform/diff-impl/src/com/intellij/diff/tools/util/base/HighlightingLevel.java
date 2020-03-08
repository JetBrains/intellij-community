// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.base;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;

public enum HighlightingLevel {
  INSPECTIONS("option.highlighting.level.inspections", AllIcons.Ide.HectorOn, rangeHighlighter -> true),

  ADVANCED("option.highlighting.level.syntax", AllIcons.Ide.HectorSyntax, rangeHighlighter -> {
    if (rangeHighlighter.getLayer() > HighlighterLayer.ADDITIONAL_SYNTAX) return false;
    HighlightInfo info = HighlightInfo.fromRangeHighlighter(rangeHighlighter);
    if (info != null && info.getSeverity().compareTo(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) >= 0) return false;
    return true;
  }),

  SIMPLE("option.highlighting.level.none", AllIcons.Ide.HectorOff, rangeHighlighter ->
    rangeHighlighter.getLayer() <= HighlighterLayer.SYNTAX);

  @NotNull private final String myTextKey;
  @Nullable private final Icon myIcon;
  @NotNull private final Condition<RangeHighlighter> myCondition;

  HighlightingLevel(@NotNull @PropertyKey(resourceBundle = DiffBundle.BUNDLE) String textKey,
                    @Nullable Icon icon,
                    @NotNull Condition<RangeHighlighter> condition) {
    myTextKey = textKey;
    myIcon = icon;
    myCondition = condition;
  }

  @Nls
  @NotNull
  public String getText() {
    return DiffBundle.message(myTextKey);
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
