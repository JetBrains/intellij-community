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

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.ActivityScope;
import com.intellij.platform.lvcs.ui.ActivityView;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ShowHistoryAction extends LocalHistoryAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      IdeaGateway gateway = getGateway();
      VirtualFile file = getFile(e);
      presentation.setEnabled(gateway != null && file != null && isEnabled(gateway, file));
    }
  }

  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    VirtualFile f = Objects.requireNonNull(getFile(e));
    if (ActivityView.isViewEnabled()) {
      ActivityView.show(p, gw, ActivityScope.fromFile(f));
    }
    else if (f.isDirectory()) {
      new DirectoryHistoryDialog(p, gw, f).show();
    }
    else {
      new FileHistoryDialog(p, gw, f).show();
    }
  }

  protected boolean isEnabled(@NotNull IdeaGateway gw, @NotNull VirtualFile f) {
    return gw.isVersioned(f) && (f.isDirectory() || gw.areContentChangesVersioned(f));
  }

  protected static @Nullable VirtualFile getFile(@NotNull AnActionEvent e) {
    return JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).single();
  }
}
