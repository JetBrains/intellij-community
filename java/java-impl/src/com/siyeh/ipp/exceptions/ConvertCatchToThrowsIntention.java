// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class ConvertCatchToThrowsIntention extends PsiBasedModCommandAction<PsiElement> {

  public ConvertCatchToThrowsIntention() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.catch.to.throws.intention.family.name");
  }

  @Override
  protected boolean isElementApplicable(@NotNull PsiElement element, @NotNull ActionContext context) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return false;
    }
    if (element instanceof PsiCodeBlock) {
      return false;
    }
    final PsiElement owner = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiClass.class, PsiLambdaExpression.class);
    if (owner instanceof PsiLambdaExpression) {
      final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(owner);
      return !(method instanceof PsiCompiledElement);
    }
    return owner instanceof PsiMethod;
  }

  private static PsiMethod @NotNull [] getSuperMethods(@NotNull PsiMethod targetMethod) {
    List<PsiMethod> result = new ArrayList<>();
    collectSuperMethods(targetMethod, result);
    return result.toArray(PsiMethod.EMPTY_ARRAY);
  }

  private static void collectSuperMethods(@NotNull PsiMethod method, @NotNull List<? super PsiMethod> result) {
    PsiMethod[] superMethods = method.findSuperMethods(true);
    for (PsiMethod superMethod : superMethods) {
      result.add(superMethod);
      collectSuperMethods(superMethod, result);
    }
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    final PsiCatchSection catchSection = (PsiCatchSection)element.getParent();
    final PsiMethod method = getMethod(catchSection);
    if (method == null) return ModCommand.nop();
    PsiType catchType = catchSection.getCatchType();
    if (catchType == null) return ModCommand.nop();
    PsiMethod[] superMethods = getSuperMethods(method);
    if (superMethods.length == 0) {
      return ModCommand.psiUpdate(catchSection, (copyCatchSection, upd) -> {
        deleteCatchAndAddToCurrentMethod(copyCatchSection, catchType);
      });
    }
    return ModCommand.chooseAction(
      IntentionPowerPackBundle.message("convert.catch.to.throws.intention.name.capitalized"),
      ModCommand.psiUpdateStep(catchSection,
                               IntentionPowerPackBundle.message("convert.catch.to.throws.super.and.current.methods"),
                               (copyCatchSection, upd) -> {
                                 List<PsiMethod> superMethodsToModify = new ArrayList<>();
                                 PsiFile containingFile = catchSection.getContainingFile();
                                 PsiFile copyCatchSectionContainingFile = copyCatchSection.getContainingFile();
                                 for (PsiMethod superMethod : superMethods) {
                                   if (!superMethod.isPhysical() || superMethod instanceof PsiCompiledElement) continue;
                                   if (superMethod.getContainingFile() == containingFile) {
                                     superMethodsToModify.add(
                                       PsiTreeUtil.findSameElementInCopy(superMethod, copyCatchSectionContainingFile));
                                   }
                                   else {
                                     PsiMethod copySuperMethod = upd.getWritable(superMethod);
                                     superMethodsToModify.add(copySuperMethod);
                                   }
                                 }
                                 deleteCatchAndAddToCurrentMethod(copyCatchSection, catchType);
                                 for (PsiMethod superMethod : superMethodsToModify) {
                                   addToThrowsList(superMethod.getThrowsList(), catchType);
                                 }
                               }),
      ModCommand.psiUpdateStep(catchSection,
                               IntentionPowerPackBundle.message("convert.catch.to.throws.only.current.method"),
                               (copyCatchSection, upd) -> deleteCatchAndAddToCurrentMethod(copyCatchSection, catchType))
    );
  }

  private static void deleteCatchAndAddToCurrentMethod(@NotNull PsiCatchSection copyCatchSection, @NotNull PsiType catchType) {
    PsiMethod copyMethod = getMethod(copyCatchSection);
    deleteCatchSection(copyCatchSection);
    if (copyMethod == null) return;
    addToThrowsList(copyMethod.getThrowsList(), catchType);
  }

  private static @Nullable PsiMethod getMethod(@NotNull PsiCatchSection catchSection) {
    final NavigatablePsiElement owner = PsiTreeUtil.getParentOfType(catchSection, PsiMethod.class, PsiLambdaExpression.class);
    final PsiMethod method;
    if (owner instanceof PsiMethod) {
      method = (PsiMethod)owner;
    }
    else if (owner instanceof PsiLambdaExpression) {
      method = LambdaUtil.getFunctionalInterfaceMethod(owner);
    }
    else {
      return null;
    }
    if (method == null) {
      return null;
    }
    return method;
  }

  private static void deleteCatchSection(PsiCatchSection catchSection) {
    final PsiTryStatement tryStatement = catchSection.getTryStatement();
    if (tryStatement.getCatchSections().length > 1 || tryStatement.getResourceList() != null || tryStatement.getFinallyBlock() != null) {
      catchSection.delete();
    }
    else {
      BlockUtils.unwrapTryBlock(tryStatement);
    }
  }

  @Override
  protected Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return Presentation.of(IntentionPowerPackBundle.message("convert.catch.to.throws.intention.name"));
  }

  private static void addToThrowsList(PsiReferenceList throwsList, PsiType catchType) {
    if (catchType instanceof PsiClassType classType) {
      final PsiClassType[] types = throwsList.getReferencedTypes();
      for (PsiClassType type : types) {
        if (catchType.equals(type)) {
          return;
        }
      }
      final Project project = throwsList.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
      throwsList.add(referenceElement);
    }
    else if (catchType instanceof PsiDisjunctionType disjunctionType) {
      final List<PsiType> disjunctions = disjunctionType.getDisjunctions();
      for (PsiType disjunction : disjunctions) {
        addToThrowsList(throwsList, disjunction);
      }
    }
  }
}