/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.simple;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class DiffOperation extends GutterIconRenderer {
  @NotNull
  public static RangeHighlighter createHighlighter(@NotNull Editor editor, @NotNull DiffOperation operation, int offset) {
    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                               HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                               null,
                                                                               HighlighterTargetArea.LINES_IN_RANGE);
    highlighter.setGutterIconRenderer(operation);
    return highlighter;
  }

  //
  // Impl
  //

  @NotNull private final String myTooltip;
  @NotNull private final Icon myIcon;

  @NotNull private final AnAction myAction;

  public DiffOperation(@NotNull String tooltip,
                       @NotNull Icon gutterIcon) {
    myTooltip = tooltip;
    myIcon = gutterIcon;
    myAction = createAction();
  }

  @CalledInAwt
  public abstract void perform(@NotNull AnActionEvent e);

  @Override
  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  @NotNull
  public String getTooltipText() {
    return myTooltip;
  }

  public boolean isNavigateAction() {
    return true;
  }

  @Nullable
  @Override
  public AnAction getClickAction() {
    return myAction;
  }

  @NotNull
  private AnAction createAction() {
    return new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        perform(e);
      }
    };
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
