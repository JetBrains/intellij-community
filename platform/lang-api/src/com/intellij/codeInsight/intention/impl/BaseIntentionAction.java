// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public abstract class BaseIntentionAction implements IntentionAction {
  private String myText = "";

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public String getText() {
    return myText;
  }

  protected void setText(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String text) {
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
   * @return true, if element belongs to project content root or is located in scratch files
   */
  public static boolean canModify(PsiElement element) {
    return element.getManager().isInProject(element) || ScratchFileService.isInScratchRoot(PsiUtilCore.getVirtualFile(element));
  }
}
