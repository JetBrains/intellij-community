// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.util;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author zajac
 */
public class SplitterItem extends ItemWrapper {
  private final @Nls String myText;

  public SplitterItem(@Nls String text) {
    myText = text;
  }

  public @Nls String getText() {
    return myText;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) { }

  @Override
  public void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected) { }

  @Override
  public void updateAccessoryView(JComponent label) { }

  @Override
  public String speedSearchText() {
    return "";
  }

  @Override
  public @Nullable @Nls String footerText() {
    return null;
  }

  @Override
  protected void doUpdateDetailView(DetailView panel, boolean editorOnly) { }

  @Override
  public boolean allowedToRemove() {
    return false;
  }

  @Override
  public void removed(Project project) { }
}
