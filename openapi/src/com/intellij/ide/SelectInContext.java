/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author kir
 */
public interface SelectInContext {

  String DATA_CONTEXT_ID = "SelectInContext";

  Project getProject();

  /** @deprecated */
  PsiFile getPsiFile();
  /** @deprecated */
  PsiElement getPsiElement();

  VirtualFile getVirtualFile();
  Object getSelectorInFile();
}
