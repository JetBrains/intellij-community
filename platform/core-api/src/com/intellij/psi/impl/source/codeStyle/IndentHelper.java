// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class IndentHelper {
  public static IndentHelper getInstance() {
    return ApplicationManager.getApplication().getService(IndentHelper.class);
  }

  /**
   * @deprecated Use {@link #getIndent(PsiFile, ASTNode)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public final int getIndent(Project project, FileType fileType, ASTNode element) {
    return getIndent(getFile(element), element);
  }

  /**
   * @deprecated Use {@link #getIndent(PsiFile, ASTNode, boolean)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public final int getIndent(Project project, FileType fileType, ASTNode element, boolean includeNonSpace) {
    return getIndent(getFile(element), element, includeNonSpace);
  }

  private static PsiFile getFile(ASTNode element) {
    return element.getPsi().getContainingFile();
  }

  public abstract int getIndent(@NotNull PsiFile file, @NotNull ASTNode element);

  public abstract int getIndent(@NotNull PsiFile file, @NotNull ASTNode element, boolean includeNonSpace);
}
