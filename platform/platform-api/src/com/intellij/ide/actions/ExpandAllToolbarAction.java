// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link ExpandAllAction} instead
 */
@Deprecated(forRemoval = true)
public class ExpandAllToolbarAction extends TreeExpandAllActionBase {
  private TreeExpander myTreeExpander;

  public ExpandAllToolbarAction(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
    ActionUtil.copyFrom(this, IdeActions.ACTION_EXPAND_ALL);
  }

  public ExpandAllToolbarAction(TreeExpander treeExpander, @NlsActions.ActionDescription String description) {
    this(treeExpander);
    getTemplatePresentation().setDescription(description);
  }

  @Override
  protected @Nullable TreeExpander getExpander(@NotNull DataContext dataContext) {
    return myTreeExpander;
  }

  public void setTreeExpander(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
  }
}
