// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.CtrlMouseAction;
import com.intellij.codeInsight.navigation.CtrlMouseData;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.HashSet;
import java.util.Set;

public final class GotoTypeDeclarationAction extends BaseCodeInsightAction implements DumbAware, CtrlMouseAction {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return GotoTypeDeclarationHandler2.INSTANCE;
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  public void update(final @NotNull AnActionEvent event) {
    if (TypeDeclarationProvider.EP_NAME.getExtensionList().size() == 0) {
      event.getPresentation().setVisible(false);
      return;
    }
    for (TypeDeclarationProvider provider : TypeDeclarationProvider.EP_NAME.getExtensionList()) {
      String text = provider.getActionText(event.getDataContext());
      if (text != null) {
        Presentation presentation = event.getPresentation();
        presentation.setText(text);
        break;
      }
    }
    super.update(event);
  }

  public static @Nullable PsiElement findSymbolType(@NotNull Editor editor, int offset) {
    final PsiElement[] psiElements = findSymbolTypes(editor, offset);
    if (psiElements != null && psiElements.length > 0) return psiElements[0];
    return null;
  }

  @VisibleForTesting
  public static PsiElement @Nullable [] findSymbolTypes(@NotNull Editor editor, int offset) {
    PsiElement targetElement = TargetElementUtil.getInstance().findTargetElement(editor,
                                                                                     TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                                     TargetElementUtil.ELEMENT_NAME_ACCEPTED |
                                                                                     TargetElementUtil.LOOKUP_ITEM_ACCEPTED,
                                                                                     offset);

    if (targetElement != null) {
      final PsiElement[] symbolType = getSymbolTypeDeclarations(targetElement, editor, offset);
      return symbolType == null ? PsiElement.EMPTY_ARRAY : symbolType;
    }

    final PsiReference psiReference = TargetElementUtil.findReference(editor, offset);
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(false);
      Set<PsiElement> types = new HashSet<>();

      for(ResolveResult r: results) {
        PsiElement element = r.getElement();
        if (element == null) continue;
        final PsiElement[] declarations = getSymbolTypeDeclarations(element, editor, offset);
        if (declarations != null) {
          for (PsiElement declaration : declarations) {
            assert declaration != null;
            types.add(declaration);
          }
        }
      }

      if (!types.isEmpty()) return PsiUtilCore.toPsiElementArray(types);
    }

    return null;
  }

  private static PsiElement @Nullable [] getSymbolTypeDeclarations(@NotNull PsiElement targetElement, Editor editor, int offset) {
    for(TypeDeclarationProvider provider: TypeDeclarationProvider.EP_NAME.getExtensionList()) {
      PsiElement[] result;
      if (provider instanceof TypeDeclarationPlaceAwareProvider) {
        result = ((TypeDeclarationPlaceAwareProvider)provider).getSymbolTypeDeclarations(targetElement, editor, offset);
      }
      else {
        result = provider.getSymbolTypeDeclarations(targetElement);
      }
      if (result != null) return result;
    }

    return null;
  }

  @Override
  public @Nullable CtrlMouseData getCtrlMouseData(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    return GotoTypeDeclarationHandler2.getCtrlMouseData(file, offset);
  }
}
