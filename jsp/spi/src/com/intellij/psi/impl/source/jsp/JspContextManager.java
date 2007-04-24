/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.jsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.jsp.JspFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class JspContextManager {

  @Nullable
  public static JspContextManager getInstance(Project project) {
    return project.getComponent(JspContextManager.class);
  }

  public abstract JspFile[] getSuitableContextFiles(@NotNull PsiFile file);

  public abstract void setContextFile(@NotNull PsiFile file, @NotNull JspFile contextFile);

  public abstract @Nullable JspFile getContextFile(@NotNull PsiFile file);

  public abstract @Nullable JspFile getConfiguredContextFile(@NotNull PsiFile file);

  public abstract @Nullable PsiFileSystemItem getContextFolder(@NotNull PsiFile file);

  public abstract void setContextFolder(@NotNull PsiFile file, @NotNull PsiFileSystemItem contextFolder);

}
