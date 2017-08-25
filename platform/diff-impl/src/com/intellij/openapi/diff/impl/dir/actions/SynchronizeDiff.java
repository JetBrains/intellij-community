/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.JBIterable;

import static com.intellij.ide.diff.DirDiffOperation.*;

/**
 * @author Konstantin Bulenkov
 */
public class SynchronizeDiff extends DirDiffAction {
  private final boolean mySelectedOnly;

  public SynchronizeDiff(DirDiffTableModel model, boolean selectedOnly) {
    super(model);
    getTemplatePresentation().setText(selectedOnly ? "Synchronize Selected" : "Synchronize All");
    getTemplatePresentation().setIcon(selectedOnly ? AllIcons.Actions.Resume : AllIcons.Actions.Rerun);
    mySelectedOnly = selectedOnly;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isEnabled()) {
      return;
    }
    boolean enabled = !JBIterable.from(mySelectedOnly ? getModel().getSelectedElements() : getModel().getElements())
      .filter(d -> d.getOperation() == COPY_FROM || d.getOperation() == COPY_TO || d.getOperation() == DELETE)
      .filter(d -> d.getSource() == null || d.getSource().isOperationsEnabled())
      .filter(d -> d.getTarget() == null || d.getTarget().isOperationsEnabled())
      .isEmpty();
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  protected void updateState(boolean state) {
    if (mySelectedOnly) {
      getModel().synchronizeSelected();
    }
    else {
      getModel().synchronizeAll();
    }
  }

  @Override
  public ShortcutSet getShortcut() {
    return CustomShortcutSet.fromString(mySelectedOnly ? "ENTER" : SystemInfo.isMac ? "meta ENTER" : "control ENTER");
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return false;
  }

  @Override
  protected boolean isFullReload() {
    return false;
  }

  @Override
  protected boolean isReloadNeeded() {
    return false;
  }
}
