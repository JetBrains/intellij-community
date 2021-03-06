// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public abstract class BaseIntentionAction implements IntentionAction {
  private @IntentionName String myText = "";

  @Override
  @IntentionName
  @NotNull
  public String getText() {
    return myText;
  }

  protected void setText(@NotNull @IntentionName String text) {
    myText = text;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public String toString() {
    return getText();
  }

  /**
   * @return true, if element belongs to project content root or is located in scratch files or in non physical file
   */
  public static boolean canModify(PsiElement element) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    PsiFile containingFile = element.getContainingFile();
    return element.getManager().isInProject(element)
           || ScratchUtil.isScratch(virtualFile)
           || (containingFile != null && containingFile.getViewProvider().getVirtualFile().getFileSystem() instanceof NonPhysicalFileSystem);
  }}
