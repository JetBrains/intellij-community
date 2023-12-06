// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.util;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ItemWrapper {
  public abstract void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected);

  public abstract void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected);

  public abstract void updateAccessoryView(JComponent label);

  public abstract String speedSearchText();

  public abstract @Nullable @Nls String footerText();

  public void updateDetailView(DetailView panel) {
    if (panel != null) {
      doUpdateDetailView(panel, panel.hasEditorOnly());
      panel.setCurrentItem(this);
    }
  }

  protected abstract void doUpdateDetailView(DetailView panel, boolean editorOnly);

  public abstract boolean allowedToRemove();

  public abstract void removed(Project project);
}
