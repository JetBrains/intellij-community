// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.CtrlMouseAction;
import com.intellij.codeInsight.navigation.CtrlMouseData;
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.navigation.CtrlMouseDataKt.*;

public class GotoImplementationAction extends BaseCodeInsightAction implements CtrlMouseAction {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new GotoImplementationHandler();
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  public void update(final @NotNull AnActionEvent event) {
    if (!DefinitionsScopedSearch.INSTANCE.hasAnyExecutors()) {
      event.getPresentation().setVisible(false);
    }
    else {
      super.update(event);
    }
  }

  private static @NotNull PsiElement @Nullable [] targetElements(@NotNull Editor editor, int offset) {
    final PsiElement element = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    return new ImplementationSearcher() {
      @Override
      protected PsiElement @NotNull [] searchDefinitions(final PsiElement element, Editor editor) {
        final List<PsiElement> found = new ArrayList<>(2);
        DefinitionsScopedSearch.search(element, getSearchScope(element, editor)).forEach(psiElement -> {
          found.add(psiElement);
          return found.size() != 2;
        });
        return PsiUtilCore.toPsiElementArray(found);
      }
    }.searchImplementations(editor, element, offset);
  }

  private static @Nullable PsiElement targetElement(@NotNull PsiElement targetElement) {
    Navigatable descriptor = EditSourceUtil.getDescriptor(targetElement);
    return descriptor != null && descriptor.canNavigate() && targetElement.isPhysical()
           ? targetElement
           : null;
  }

  @Override
  public @Nullable CtrlMouseData getCtrlMouseData(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    final PsiElement elementAtPointer = file.findElementAt(offset);
    if (elementAtPointer == null) {
      return null;
    }
    PsiElement[] targetElements = targetElements(editor, offset);
    if (targetElements == null || targetElements.length == 0) {
      return null;
    }
    else if (targetElements.length > 1) {
      return multipleTargetsCtrlMouseData(getReferenceRanges(elementAtPointer));
    }
    else {
      PsiElement targetElement = targetElement(targetElements[0]);
      if (targetElement == null) {
        return null;
      }
      return psiCtrlMouseData(elementAtPointer, targetElement);
    }
  }
}
