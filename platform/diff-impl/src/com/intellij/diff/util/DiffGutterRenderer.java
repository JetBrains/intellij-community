// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.NonHideableIconGutterMark;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class DiffGutterRenderer extends GutterIconRenderer implements NonHideableIconGutterMark {
  private final @NotNull Icon myIcon;
  private final @Nullable @NlsContexts.Tooltip String myTooltip;

  public DiffGutterRenderer(@NotNull Icon icon, @Nullable @NlsContexts.Tooltip String tooltip) {
    myIcon = icon;
    myTooltip = tooltip;
  }

  @Override
  public @NotNull Icon getIcon() {
    return myIcon;
  }

  @Override
  public @NlsContexts.Tooltip @Nullable String getTooltipText() {
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

  @Override
  public @NotNull Alignment getAlignment() {
    return Alignment.LEFT;
  }

  @Override
  public @Nullable AnAction getClickAction() {
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
    if (mouseEvent == null || mouseEvent.getButton() == MouseEvent.BUTTON1) {
      handleMouseClick();
    }
  }

  protected abstract void handleMouseClick();
}
