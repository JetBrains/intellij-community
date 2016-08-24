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

package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

public class ShowSelectionHistoryAction extends ShowHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull VirtualFile f, @NotNull AnActionEvent e) {
    VcsSelection sel = notNull(getSelection(e));

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    new SelectionHistoryDialog(p, gw, f, from, to).show();
  }

  @Override
  protected String getText(@NotNull AnActionEvent e) {
    VcsSelection sel = getSelection(e);
    return sel == null ? super.getText(e) : sel.getActionName();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getData(CommonDataKeys.EDITOR) == null) {
      e.getPresentation().setVisible(false);
    }
    else {
      super.update(e);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull LocalHistoryFacade vcs, @NotNull IdeaGateway gw, @Nullable VirtualFile f, @NotNull AnActionEvent e) {
    return super.isEnabled(vcs, gw, f, e) && !notNull(f).isDirectory() && getSelection(e) != null;
  }

  @Nullable
  private static VcsSelection getSelection(@NotNull AnActionEvent e) {
    return VcsSelectionUtil.getSelection(VcsContextWrapper.createCachedInstanceOn(e));
  }
}
