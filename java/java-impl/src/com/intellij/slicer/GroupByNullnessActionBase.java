/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public abstract class GroupByNullnessActionBase extends AnAction {
  protected final SliceTreeBuilder myTreeBuilder;
  private static final String TEXT = "Group by leaf expression nullness";

  protected GroupByNullnessActionBase(@NotNull SliceTreeBuilder treeBuilder) {
    super(TEXT, "Determine whether null can flow into this expression", AllIcons.Debugger.Db_disabled_breakpoint_process);
    myTreeBuilder = treeBuilder;
  }

  protected abstract boolean isAvailable();

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(TEXT + (myTreeBuilder.analysisInProgress ? " (Analysis in progress)" : ""));
    e.getPresentation().setEnabled(
      !myTreeBuilder.analysisInProgress && myTreeBuilder.dataFlowToThis && !myTreeBuilder.splitByLeafExpressions && isAvailable()
    );
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myTreeBuilder.switchToLeafNulls();
  }
}
