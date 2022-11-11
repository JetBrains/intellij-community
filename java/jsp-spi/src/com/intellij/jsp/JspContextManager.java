// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.jsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.BaseJspFile;
import com.intellij.psi.jsp.JspFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

public abstract class JspContextManager {

  public static JspContextManager getInstance(@NotNull Project project) {
    return project.getService(JspContextManager.class);
  }

  public abstract BaseJspFile[] getSuitableContextFiles(@NotNull PsiFile file);

  public abstract void setContextFile(@NotNull PsiFile file, @Nullable BaseJspFile contextFile);

  public abstract @Nullable BaseJspFile getContextFile(@NotNull PsiFile file);

  public abstract @Nullable JspFile getConfiguredContextFile(@NotNull PsiFile file);

  public @NotNull
  BaseJspFile getRootContextFile(@NotNull BaseJspFile file) {
    BaseJspFile rootContext = file;
    HashSet<BaseJspFile> recursionPreventer = new HashSet<>();
    do {
      recursionPreventer.add(rootContext);
      BaseJspFile context = getContextFile(rootContext);
      if (context == null || recursionPreventer.contains(context)) break;
      rootContext = context;
    }
    while (true);

    return rootContext;
  }
}
