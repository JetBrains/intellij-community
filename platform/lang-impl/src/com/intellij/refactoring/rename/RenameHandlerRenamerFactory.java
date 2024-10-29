// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.refactoring.actions.BaseRefactoringAction.findRefactoringTargetInEditor;
import static com.intellij.refactoring.actions.BaseRefactoringAction.getPsiElementArray;

@ApiStatus.Internal
public final class RenameHandlerRenamerFactory implements RenamerFactory {

  private static boolean isEnabledOnDataContext(@NotNull DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().hasAvailableHandler(dataContext);
  }

  /**
   * Greatly simplified version of {@link com.intellij.refactoring.actions.BaseRefactoringAction#update}.
   */
  private static boolean isAvailable(@NotNull DataContext dataContext) {
    PsiFile file = dataContext.getData(CommonDataKeys.PSI_FILE);
    if (file instanceof PsiCompiledElement) {
      return false;
    }
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      return isEnabledOnDataContext(dataContext)
             || RenameElementAction.isRenameEnabledOnElements(getPsiElementArray(dataContext));
    }
    else {
      return file != null
             && isEnabledOnDataContext(dataContext)
             && findRefactoringTargetInEditor(dataContext, language -> true) != null;
    }
  }

  @Override
  public @NotNull Collection<? extends @NotNull Renamer> createRenamers(@NotNull DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return Collections.emptyList();
    }

    if (!isAvailable(dataContext)) {
      return Collections.emptyList();
    }

    int eventCount = IdeEventQueue.getInstance().getEventCount();
    return ContainerUtil.map(
      RenameHandlerRegistry.getInstance().getRenameHandlers(dataContext),
      renameHandler -> new RenameHandler2Renamer(project, dataContext, renameHandler, eventCount)
    );
  }
}
