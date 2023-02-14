// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ViewClassHierarchyAction extends ChangeViewTypeActionBase {
  public ViewClassHierarchyAction() {
    super(IdeBundle.messagePointer("action.view.class.hierarchy"),
          IdeBundle.messagePointer("action.description.view.class.hierarchy"), AllIcons.Hierarchy.Class);
  }

  @Override
  protected @Nls String getTypeName() {
    return TypeHierarchyBrowserBase.getTypeHierarchyType();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    TypeHierarchyBrowserBase browser = getHierarchyBrowser(event.getDataContext());
    event.getPresentation().setEnabled(browser != null && !browser.isInterface());
  }
}
