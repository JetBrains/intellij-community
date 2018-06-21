// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MultiLineTodoLocalityDetector implements ChangeLocalityDetector {
  @Nullable
  @Override
  public PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement) {
    if (!(changedElement instanceof PsiComment) || !TodoConfiguration.getInstance().isMultiLine()) return null;
    PsiComment commentAbove = findAdjacentComment(changedElement, true);
    PsiComment commentBelow = findAdjacentComment(changedElement, false);
    if (commentAbove == null && commentBelow == null) return null;
    return PsiTreeUtil.findCommonParent(changedElement, commentAbove, commentBelow);
  }

  private static PsiComment findAdjacentComment(PsiElement element, boolean above) {
    PsiElement currentElement = element;
    PsiComment lastComment = null;
    boolean nextLine = false;
    while (true) {
      currentElement = above ? PsiTreeUtil.prevLeaf(currentElement) : PsiTreeUtil.nextLeaf(currentElement);
      if (currentElement == null) break;
      String elementText = currentElement.getText();
      if (elementText == null) break;
      int newLines = StringUtil.countNewLines(elementText);
      if (newLines == 0 && !nextLine) continue;
      if (currentElement instanceof PsiComment) lastComment = (PsiComment)currentElement;
      if (newLines > (nextLine ? 0 : 1)) break;
      nextLine = true;
    }
    return lastComment;
  }
}
