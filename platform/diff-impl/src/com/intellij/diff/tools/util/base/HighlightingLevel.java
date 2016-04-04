/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.base;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public enum HighlightingLevel {
  INSPECTIONS("Inspections", AllIcons.Ide.HectorOn, new Condition<RangeHighlighter>() {
    @Override
    public boolean value(RangeHighlighter rangeHighlighter) {
      return true;
    }
  }),

  ADVANCED("Syntax", AllIcons.Ide.HectorSyntax, new Condition<RangeHighlighter>() {
    @Override
    public boolean value(RangeHighlighter rangeHighlighter) {
      return rangeHighlighter.getLayer() <= HighlighterLayer.ADDITIONAL_SYNTAX;
    }
  }),

  SIMPLE("None", AllIcons.Ide.HectorOff, new Condition<RangeHighlighter>() {
    @Override
    public boolean value(RangeHighlighter rangeHighlighter) {
      return rangeHighlighter.getLayer() <= HighlighterLayer.SYNTAX;
    }
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
