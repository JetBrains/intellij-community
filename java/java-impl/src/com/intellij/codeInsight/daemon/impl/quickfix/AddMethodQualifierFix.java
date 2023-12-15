// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNullElse;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodQualifierFix extends PsiBasedModCommandAction<PsiMethodCallExpression> {
  private enum SearchMode { MAX_2_CANDIDATES, FULL_SEARCH }

  public AddMethodQualifierFix(@NotNull PsiMethodCallExpression methodCallExpression) {
    super(methodCallExpression);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.method.qualifier.fix.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    if (element.getMethodExpression().getQualifierExpression() != null) return null;
    List<PsiVariable> candidates = findCandidates(element, SearchMode.MAX_2_CANDIDATES);
    if (candidates.isEmpty()) return null;
    if (candidates.size() == 1) {
      return Presentation.of(QuickFixBundle.message("add.method.qualifier.fix.text", candidates.get(0).getName()));
    }
    return Presentation.of(getFamilyName());
  }

  @NotNull
  private static List<PsiVariable> findCandidates(@NotNull PsiMethodCallExpression methodCallElement, @NotNull SearchMode mode) {
    String methodName = methodCallElement.getMethodExpression().getReferenceName();
    if (methodName == null) {
      return Collections.emptyList();
    }

    List<PsiVariable> candidates = new ArrayList<>();
    for (PsiVariable var : CreateFromUsageUtils.guessMatchingVariables(methodCallElement)) {
      if (var.getName() == null) {
        continue;
      }
      PsiType type = var.getType();
      if (!(type instanceof PsiClassType)) {
        continue;
      }
      PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass == null) {
        continue;
      }
      if (resolvedClass.findMethodsByName(methodName, true).length > 0) {
        candidates.add(var);
        if (mode == SearchMode.MAX_2_CANDIDATES && candidates.size() >= 2) {
          break;
        }
      }
    }
    return List.copyOf(candidates);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    List<PsiVariable> candidates = findCandidates(element, IntentionPreviewUtils.isIntentionPreviewActive() ?
                                                           SearchMode.MAX_2_CANDIDATES : SearchMode.FULL_SEARCH);
    SmartPsiElementPointer<PsiMethodCallExpression> pointer = SmartPointerManager.createPointer(element);
    List<ModCommandAction> qualifyActions = ContainerUtil.map(candidates, candidate -> createAction(candidate, pointer));
    return ModCommand.chooseAction(QuickFixBundle.message("add.qualifier"), qualifyActions);
  }

  @NotNull
  private static ModCommandAction createAction(PsiVariable candidate, SmartPsiElementPointer<PsiMethodCallExpression> pointer) {
    return ModCommand.psiUpdateStep(
        candidate, requireNonNullElse(candidate.getName(), ""), (var, updater) -> {
          PsiMethodCallExpression call = updater.getWritable(pointer.getElement());
          if (call == null) return;
          replaceWithQualifier(var, call);
          updater.moveCaretTo(call.getTextOffset() + call.getTextLength());
        }, var -> requireNonNullElse(var.getNameIdentifier(), var).getTextRange())
      .withPresentation(p -> p.withIcon(candidate.getIcon(0)));
  }

  private static void replaceWithQualifier(@NotNull PsiVariable qualifier, @NotNull PsiMethodCallExpression oldExpression) {
    String qualifierPresentableText = qualifier.getName();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(qualifier.getProject());
    PsiMethodCallExpression expression = (PsiMethodCallExpression)elementFactory
      .createExpressionFromText(qualifierPresentableText + "." + oldExpression.getMethodExpression().getReferenceName() + "()", null);
    oldExpression.getMethodExpression().replace(expression.getMethodExpression());
  }
}