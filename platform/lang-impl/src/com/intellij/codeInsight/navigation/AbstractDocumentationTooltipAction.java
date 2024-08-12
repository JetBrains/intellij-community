// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.PatchedWeakReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Expands {@link AnAction} contract for documentation-related actions that may be called from the IDE tooltip.
 */
public abstract class AbstractDocumentationTooltipAction extends AnAction {

  private @Nullable WeakReference<PsiElement> myDocAnchor;
  private @Nullable WeakReference<PsiElement> myOriginalElement;

  public void setDocInfo(@NotNull PsiElement docAnchor, @NotNull PsiElement originalElement) {
    myDocAnchor = new PatchedWeakReference<>(docAnchor);
    myOriginalElement = new PatchedWeakReference<>(originalElement);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(getDocInfo() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Pair<PsiElement, PsiElement> info = getDocInfo();
    if (info == null) {
      return;
    }
    doActionPerformed(e.getDataContext(), info.first, info.second);
    myDocAnchor = null;
    myOriginalElement = null;
  }

  protected abstract void doActionPerformed(@NotNull DataContext context,
                                            @NotNull PsiElement docAnchor,
                                            @NotNull PsiElement originalElement);

  private @Nullable Pair<PsiElement/* doc anchor */, PsiElement /* original element */> getDocInfo() {
    PsiElement docAnchor = SoftReference.dereference(myDocAnchor);
    if (docAnchor == null) {
      return null;
    }
    PsiElement originalElement = SoftReference.dereference(myOriginalElement);
    if (originalElement == null) {
      return null;
    }
    return Pair.create(docAnchor, originalElement);
  }
}
