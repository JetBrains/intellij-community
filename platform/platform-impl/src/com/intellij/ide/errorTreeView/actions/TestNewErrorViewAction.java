// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView.actions;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 */
public class TestNewErrorViewAction extends TestErrorViewAction{
  @Override
  protected ErrorTreeView createView(Project project) {
    return new NewErrorTreeViewPanel(project, null);
  }

  @Override
  @NonNls
  protected String getContentName() {
    return "NewView";
  }
}
