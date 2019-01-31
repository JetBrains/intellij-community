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
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.NonHideableIconGutterMark;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class DiffGutterRenderer extends GutterIconRenderer implements NonHideableIconGutterMark {
  @NotNull private final Icon myIcon;
  @Nullable private final String myTooltip;

  public DiffGutterRenderer(@NotNull Icon icon, @Nullable String tooltip) {
    myIcon = icon;
    myTooltip = tooltip;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @NotNull
  @Override
  public Alignment getAlignment() {
    return Alignment.LEFT;
  }

  @Nullable
  @Override
  public AnAction getClickAction() {
    return DumbAwareAction.create(e -> performAction(e));
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  protected void performAction(@NotNull AnActionEvent e) {
    MouseEvent mouseEvent = ObjectUtils.tryCast(e.getInputEvent(), MouseEvent.class);
    if (mouseEvent == null ||
        mouseEvent.getButton() == MouseEvent.BUTTON1 && mouseEvent.getClickCount() == 1) {
      handleMouseClick();
    }
  }

  protected abstract void handleMouseClick();
}
