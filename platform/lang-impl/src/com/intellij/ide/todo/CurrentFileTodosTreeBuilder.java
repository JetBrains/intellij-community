// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class CurrentFileTodosTreeBuilder extends TodoTreeBuilder {

  public CurrentFileTodosTreeBuilder(@NotNull JTree tree,
                                     @NotNull Project project) {
    super(tree, project);
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new CurrentFileTodosTreeStructure(myProject);
  }

  @Override
  protected void collectFiles(@NotNull Consumer<? super @NotNull PsiFile> consumer) {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    PsiFile psiFile = treeStructure.getFile();
    if (psiFile != null && treeStructure.accept(psiFile)) {
      consumer.accept(psiFile);
    }
  }

  /**
   * @see CurrentFileTodosTreeStructure#setFile
   */
  public void setFile(PsiFile file) {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    treeStructure.setFile(file);
    rebuildCache();
  }
}
