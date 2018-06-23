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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class TreeExpandAllActionBase extends AnAction implements DumbAware {
  @Nullable
  protected abstract TreeExpander getExpander(DataContext dataContext);

  public final void actionPerformed(AnActionEvent e) {
    TreeExpander expander = getExpander(e.getDataContext());
    if (expander == null) return;
    if (!expander.canExpand()) return;
    expander.expandAll();
  }

  public final void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    TreeExpander expander = getExpander(event.getDataContext());
    presentation.setVisible(expander == null || expander.isVisible(event));
    presentation.setEnabled(expander != null && expander.canExpand());
  }
}
