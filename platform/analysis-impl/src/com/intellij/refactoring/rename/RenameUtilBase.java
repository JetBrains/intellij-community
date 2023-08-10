// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenameUtilBase {

  private static final Logger LOG = Logger.getInstance(RenameUtilBase.class);

  private RenameUtilBase() {
  }


  public static void doRenameGenericNamedElement(@NotNull PsiElement namedElement,
                                                 String newName,
                                                 UsageInfo[] usages,
                                                 @Nullable RefactoringElementListener listener) {
    PsiWritableMetaData writableMetaData = null;
    if (namedElement instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)namedElement).getMetaData();
      if (metaData instanceof PsiWritableMetaData) {
        writableMetaData = (PsiWritableMetaData)metaData;
      }
    }
    if (writableMetaData == null && !(namedElement instanceof PsiNamedElement)) {
      LOG.error("Unknown element type:" + namedElement);
    }

    boolean hasBindables = false;
    for (UsageInfo usage : usages) {
      if (!(usage.getReference() instanceof BindablePsiReference)) {
        rename(usage, newName);
      }
      else {
        hasBindables = true;
      }
    }

    if (writableMetaData != null) {
      writableMetaData.setName(newName);
    }
    else {
      PsiElement namedElementAfterRename = ((PsiNamedElement)namedElement).setName(newName);
      if (namedElementAfterRename != null) namedElement = namedElementAfterRename;
    }

    if (hasBindables) {
      for (UsageInfo usage : usages) {
        final PsiReference ref = usage.getReference();
        if(ref != null)
          renameReference(namedElement, newName, ref);
      }
    }
    if (listener != null) {
      listener.elementRenamed(namedElement);
    }
  }

  public static void renameReference(@NotNull PsiElement namedElement, String newName, @NotNull PsiReference ref) {
    if (ref instanceof BindablePsiReference) {
      boolean fallback = true;
      if (!(ref instanceof FragmentaryPsiReference && ((FragmentaryPsiReference)ref).isFragmentOnlyRename())) {
        try {
          ref.bindToElement(namedElement);
          fallback = false;
        }
        catch (IncorrectOperationException ignored) {
        }
      }
      if (fallback) {//fall back to old scheme
        ref.handleElementRename(newName);
      }
    }
  }

  public static void rename(UsageInfo info, String newName) {
    if (info.getElement() == null) return;
    PsiReference ref = info.getReference();
    if (ref == null) return;
    ref.handleElementRename(newName);
  }

  @ApiStatus.Internal
  public static UsageInfo createMoveRenameUsageInfo(@NotNull PsiElement element,
                                                    @NotNull PsiReference ref,
                                                    @NotNull PsiElement referenceElement) {
    return new MoveRenameUsageInfo(referenceElement, ref,
                                   ref.getRangeInElement().getStartOffset(),
                                   ref.getRangeInElement().getEndOffset(),
                                   element,
                                   ref.resolve() == null &&
                                   !(ref instanceof PsiPolyVariantReference &&
                                     ((PsiPolyVariantReference)ref).multiResolve(true).length > 0));
  }
}