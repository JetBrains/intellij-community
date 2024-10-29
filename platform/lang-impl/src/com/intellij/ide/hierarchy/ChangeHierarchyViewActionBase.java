// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class ChangeHierarchyViewActionBase extends ToggleAction {
  public ChangeHierarchyViewActionBase(String text, String description, Icon icon) {
    this(() -> text, () -> description, icon);
  }

  public ChangeHierarchyViewActionBase(@NotNull Supplier<String> text, @NotNull Supplier<String> description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final boolean isSelected(@NotNull AnActionEvent event) {
    HierarchyBrowserBaseEx browser = getHierarchyBrowser(event.getDataContext());
    return browser != null && getTypeName().equals(browser.getCurrentViewType());
  }

  protected abstract @Nls String getTypeName();

  @Override
  public final void setSelected(@NotNull AnActionEvent event, boolean flag) {
    if (flag) {
      HierarchyBrowserBaseEx browser = getHierarchyBrowser(event.getDataContext());
      ApplicationManager.getApplication().invokeLater(() -> {
        if (browser != null) {
          browser.changeView(getTypeName());
        }
      });
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    HierarchyBrowserBaseEx browser = getHierarchyBrowser(event.getDataContext());
    presentation.setEnabled(browser != null && browser.isValidBase());
  }

  protected @Nullable HierarchyBrowserBaseEx getHierarchyBrowser(@NotNull DataContext dataContext) {
    return HierarchyBrowserBaseEx.HIERARCHY_BROWSER.getData(dataContext);
  }
}
