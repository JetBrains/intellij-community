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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistoryAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    Presentation p = e.getPresentation();
    if (getProject(e) == null) {
      p.setVisible(false);
      p.setEnabled(false);
      return;
    }
    p.setVisible(true);
    p.setText(getText(e), true);
    p.setEnabled(isEnabled(getVcs(), getGateway(), getFile(e), e));
  }

  protected String getText(AnActionEvent e) {
    return e.getPresentation().getTextWithMnemonic();
  }

  protected boolean isEnabled(LocalHistoryFacade vcs, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return true;
  }

  protected LocalHistoryFacade getVcs() {
    return LocalHistoryImpl.getInstanceImpl().getFacade();
  }

  protected IdeaGateway getGateway() {
    return LocalHistoryImpl.getInstanceImpl().getGateway();
  }

  protected VirtualFile getFile(AnActionEvent e) {
    VirtualFile[] ff = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (ff == null || ff.length == 0) return null;

    VirtualFile commonParent = ff[0];
    for (int i = 1; i < ff.length; i++) {
      commonParent = VfsUtil.getCommonAncestor(commonParent, ff[i]);
      if (commonParent == null) break;
    }
    return commonParent;
  }

  protected Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }
}
