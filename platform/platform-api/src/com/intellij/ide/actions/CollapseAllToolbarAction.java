/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;

public class CollapseAllToolbarAction extends TreeCollapseAllActionBase {
  private TreeExpander myTreeExpander;

  public CollapseAllToolbarAction(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
    ActionUtil.copyFrom(this, IdeActions.ACTION_COLLAPSE_ALL);
  }

  public CollapseAllToolbarAction(TreeExpander treeExpander, String description) {
    this(treeExpander);
    getTemplatePresentation().setDescription(description);
  }

  @Override
  protected TreeExpander getExpander(DataContext dataContext) {
    return myTreeExpander;
  }

  public void setTreeExpander(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
  }
}
