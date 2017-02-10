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
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.UtilKt.getIfSingle;

public abstract class LocalHistoryAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();

    if (e.getProject() == null) {
      p.setEnabledAndVisible(false);
    }
    else {
      p.setVisible(true);
      p.setText(getText(e), true);

      LocalHistoryFacade vcs = getVcs();
      IdeaGateway gateway = getGateway();
      p.setEnabled(vcs != null && gateway != null && isEnabled(vcs, gateway, e));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(e.getRequiredData(CommonDataKeys.PROJECT), notNull(getGateway()), e);
  }

  protected String getText(@NotNull AnActionEvent e) {
    return e.getPresentation().getTextWithMnemonic();
  }

  protected boolean isEnabled(@NotNull LocalHistoryFacade vcs, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    return isEnabled(vcs, gw, getFile(e), e);
  }

  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    actionPerformed(p, gw, notNull(getFile(e)), e);
  }

  protected boolean isEnabled(@NotNull LocalHistoryFacade vcs, @NotNull IdeaGateway gw, @Nullable VirtualFile f, @NotNull AnActionEvent e) {
    return true;
  }

  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull VirtualFile f, @NotNull AnActionEvent e) {
  }

  @Nullable
  protected LocalHistoryFacade getVcs() {
    return LocalHistoryImpl.getInstanceImpl().getFacade();
  }

  @Nullable
  protected IdeaGateway getGateway() {
    return LocalHistoryImpl.getInstanceImpl().getGateway();
  }

  @Nullable
  protected VirtualFile getFile(@NotNull AnActionEvent e) {
    return getIfSingle(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM));
  }
}
