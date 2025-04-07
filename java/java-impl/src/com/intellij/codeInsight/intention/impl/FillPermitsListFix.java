// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public class FillPermitsListFix extends PsiBasedModCommandAction<PsiIdentifier> {

  public FillPermitsListFix(PsiIdentifier classIdentifier) {
    super(classIdentifier);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiIdentifier element) {
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiIdentifier startElement) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(startElement, PsiClass.class);
    if (psiClass == null) return ModCommand.nop();
    PsiJavaFile psiJavaFile = tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return ModCommand.nop();
    Set<PsiClass> permittedClasses = ContainerUtil.map2Set(psiClass.getPermitsListTypes(), PsiClassType::resolve);
    return getMissingInheritors(psiJavaFile, psiClass, permittedClasses);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.fill.permits.list.fix.name");
  }

  private static @NotNull ModCommand getMissingInheritors(@NotNull PsiJavaFile psiJavaFile,
                                                          @NotNull PsiClass psiClass,
                                                          @NotNull Set<PsiClass> permittedClasses) {
    Collection<String> missingInheritors = new SmartList<>();
    PsiJavaModule module = JavaPsiModuleUtil.findDescriptorByElement(psiClass);
    for (PsiClass inheritor : DirectClassInheritorsSearch.searchAllSealedInheritors(psiClass, psiClass.getUseScope()).asIterable()) {
      String errorTitle = SealedUtils.checkInheritor(psiJavaFile, module, inheritor);
      if (errorTitle != null) {
        return ModCommand.error(JavaBundle.message(errorTitle));
      }
      String qualifiedName = Objects.requireNonNull(inheritor.getQualifiedName());
      if (!ContainerUtil.exists(permittedClasses, cls -> cls.isEquivalentTo(inheritor))) missingInheritors.add(qualifiedName);
    }

    if (missingInheritors.isEmpty()) {
      String message = JavaBundle.message("inspection.fill.permits.list.no.missing.inheritors");
      return ModCommand.error(message);
    }
    return ModCommand.psiUpdate(psiClass, cls -> SealedUtils.fillPermitsList(cls, missingInheritors));
  }
}
