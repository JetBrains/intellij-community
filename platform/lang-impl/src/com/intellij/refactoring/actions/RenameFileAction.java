// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RenameFileAction extends AnAction implements ActionPromoter {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    assert file != null;
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    PsiElementRenameHandler.invoke(file, project, file, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Presentation presentation = e.getPresentation();
    String place = e.getPlace();
    boolean enabled =
      file != null && file.isWritable()
      && Objects.nonNull(file.getVirtualFile()) && !(file.getVirtualFile().getFileSystem().isReadOnly())
      && (enabledInProjectView(file) || !ActionPlaces.PROJECT_VIEW_POPUP.equals(place))
      && !ActionPlaces.EDITOR_POPUP.equals(place) && e.getData(CommonDataKeys.PROJECT) != null;
    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public @Nullable List<AnAction> suppress(@NotNull List<? extends AnAction> actions,
                                           @NotNull DataContext context) {
    return CommonDataKeys.EDITOR.getData(context) != null && ContainerUtil.findInstance(actions, RenameElementAction.class) != null 
           ? Collections.singletonList(this) : null;
  }

  private static boolean enabledInProjectView(@NotNull PsiFile file) {
    return ContainerUtil.exists(RenameFileActionProvider.EP_NAME.getExtensionList(), provider -> provider.enabledInProjectView(file));
  }
}
