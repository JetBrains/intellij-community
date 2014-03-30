/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Danila Ponomarenko
 */
public class RemoveRedundantArgumentsFix implements IntentionAction {
  private final PsiMethod myTargetMethod;
  private final PsiExpression[] myArguments;
  private final PsiSubstitutor mySubstitutor;

  private RemoveRedundantArgumentsFix(@NotNull PsiMethod targetMethod,
                                     @NotNull PsiExpression[] arguments,
                                     @NotNull PsiSubstitutor substitutor) {
    myTargetMethod = targetMethod;
    myArguments = arguments;
    mySubstitutor = substitutor;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("remove.redundant.arguments.text", JavaHighlightUtil.formatMethod(myTargetMethod));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("remove.redundant.arguments.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) return false;
    for (PsiExpression expression : myArguments) {
      if (!expression.isValid()) return false;
    }
    if (!mySubstitutor.isValid()) return false;

    return findRedundantArgument(myArguments, myTargetMethod.getParameterList().getParameters(), mySubstitutor) != null;
  }

  @Nullable
  private static PsiExpression[] findRedundantArgument(@NotNull PsiExpression[] arguments,
                                                       @NotNull PsiParameter[] parameters,
                                                       @NotNull PsiSubstitutor substitutor) {
    if (arguments.length <= parameters.length) return null;

    for (int i = 0; i < parameters.length; i++) {
      final PsiExpression argument = arguments[i];
      final PsiParameter parameter = parameters[i];

      final PsiType argumentType = argument.getType();
      if (argumentType == null) return null;
      final PsiType parameterType = substitutor.substitute(parameter.getType());

      if (!TypeConversionUtil.isAssignable(parameterType, argumentType)) {
        return null;
      }
    }

    return Arrays.copyOfRange(arguments, parameters.length, arguments.length);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiExpression[] redundantArguments = findRedundantArgument(myArguments, myTargetMethod.getParameterList().getParameters(), mySubstitutor);
    if (redundantArguments != null) {
      for (PsiExpression argument : redundantArguments) {
        argument.delete();
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void registerIntentions(@NotNull JavaResolveResult[] candidates,
                                        @NotNull PsiExpressionList arguments,
                                        @Nullable HighlightInfo highlightInfo,
                                        TextRange fixRange) {
    for (JavaResolveResult candidate : candidates) {
      registerIntention(arguments, highlightInfo, fixRange, candidate, arguments);
    }
  }

  private static void registerIntention(@NotNull PsiExpressionList arguments,
                                        @Nullable HighlightInfo highlightInfo,
                                        TextRange fixRange,
                                        @NotNull JavaResolveResult candidate,
                                        @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method != null && context.getManager().isInProject(method)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, new RemoveRedundantArgumentsFix(method, arguments.getExpressions(), substitutor));
    }
  }
}
