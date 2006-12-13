/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.jsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class JspContextManager {

  public static JspContextManager getInstance(Project project) {
    return project.getComponent(JspContextManager.class);
  }

  public abstract boolean processContextElements(@NotNull FileViewProvider viewProvider,
                                                 PsiScopeProcessor processor,
                                                 PsiSubstitutor substitutor,
                                                 final PsiElement place);

  public abstract JspFile[] getSuitableContextFiles(@NotNull JspFile file);

  public abstract void setContextFile(@NotNull JspFile file, @NotNull JspFile contextFile);

  public abstract @Nullable JspFile getContextFile(final @NotNull JspFile jspFile);

  public abstract @Nullable JspFile getConfiguredContextFile(JspFile file);
}
