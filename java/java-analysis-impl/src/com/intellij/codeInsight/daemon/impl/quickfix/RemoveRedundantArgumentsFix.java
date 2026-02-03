// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RemoveRedundantArgumentsFix extends PsiUpdateModCommandAction<PsiExpressionList> {
  private final PsiMethod myTargetMethod;
  private final PsiSubstitutor mySubstitutor;

  private RemoveRedundantArgumentsFix(@NotNull PsiMethod targetMethod,
                                      @NotNull PsiExpressionList arguments,
                                      @NotNull PsiSubstitutor substitutor) {
    super(arguments);
    myTargetMethod = targetMethod;
    mySubstitutor = substitutor;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("remove.redundant.arguments.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList args) {
    if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) return null;
    if (!mySubstitutor.isValid()) return null;
    PsiExpression[] redundant =
      findRedundantArgument(args.getExpressions(), myTargetMethod.getParameterList().getParameters(), mySubstitutor);
    if (redundant == null) {
      return null;
    }
    return Presentation.of(QuickFixBundle.message("remove.redundant.arguments.text", JavaHighlightUtil.formatMethod(myTargetMethod), 
                                                  redundant.length));
  }

  /**
   * @return array of redundant arguments or null if it is not possible to remove some elements from arguments list to make call compilable
   */
  private static PsiExpression @Nullable [] findRedundantArgument(PsiExpression @NotNull [] arguments,
                                                                  PsiParameter @NotNull [] parameters,
                                                                  @NotNull PsiSubstitutor substitutor) {
    /*
     * Greedy algorithm with linear complexity to find redundant arguments.
     * Iterate through argument and parameter lists using separate indexes.
     * Advance both indexes when argument type is assignable to parameter type,
     * otherwise add current argument to redundant list and advance parameter index.
     */
    List<PsiExpression> reduntantArguments = new ArrayList<>();
    PsiType varargsComponentType = varargsComponentType(parameters);
    int argumentCount = arguments.length;
    int parameterCount = parameters.length - (varargsComponentType == null ? 0 : 1);
    if (argumentCount <= parameterCount) {
      return null;
    }

    int argumentIndex = 0;
    int parameterIndex = 0;
    while (argumentIndex < argumentCount && parameterIndex < parameterCount) {
      PsiExpression argument = arguments[argumentIndex];
      PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return null;
      }
      PsiType parameterType = substitutor.substitute(parameters[parameterIndex].getType());

      if (TypeConversionUtil.isAssignable(parameterType, argumentType)) {
        argumentIndex++;
        parameterIndex++;
      }
      else {
        reduntantArguments.add(argument);
        argumentIndex++;
      }
    }

    if (varargsComponentType != null) {
      boolean matchedVarargsElement = false; // have we found argument which type is assignable to varargs component type
      boolean matchedVarargsArray = false; // have we found argument which type is an array assignable to varrargs array
      PsiType parameterType = substitutor.substitute(varargsComponentType);
      while (argumentIndex < argumentCount) {
        PsiExpression argument = arguments[argumentIndex];
        PsiType argumentType = argument.getType();
        if (argumentType == null) {
          return null;
        }
        if (matchedVarargsArray) {
          reduntantArguments.add(argument);
        } else {
          if (!matchedVarargsElement && isArgumentTypeAssignableToVarargsArrayType(parameters, substitutor, argumentType)) {
            matchedVarargsArray = true;
          } else {
            if (!TypeConversionUtil.isAssignable(parameterType, argumentType)) {
              reduntantArguments.add(argument);
            } else {
              matchedVarargsElement = true;
            }
          }
        }
        argumentIndex++;
      }
    }

    if (parameterIndex < parameterCount) {
      return null;
    }

    while (argumentIndex < argumentCount) {
      reduntantArguments.add(arguments[argumentIndex++]);
    }

    return reduntantArguments.toArray(PsiExpression.EMPTY_ARRAY);
  }

  private static boolean isArgumentTypeAssignableToVarargsArrayType(PsiParameter @NotNull [] parameters,
                                                                    @NotNull PsiSubstitutor substitutor,
                                                                    PsiType argumentType) {
    PsiType varargsArrayType = substitutor.substitute(varargsArrayType(parameters));
    return varargsArrayType != null && TypeConversionUtil.isAssignable(varargsArrayType, argumentType);
  }

  private static @Nullable PsiType varargsComponentType(PsiParameter @NotNull [] parameters) {
    int length = parameters.length;
    if (length == 0) return null;
    PsiType type = parameters[length - 1].getType();
    if (type instanceof PsiEllipsisType ellipsisType) {
      return ellipsisType.getComponentType();
    }
    return null;
  }

  private static @Nullable PsiType varargsArrayType(PsiParameter @NotNull [] parameters) {
    int length = parameters.length;
    if (length == 0) return null;
    PsiType type = parameters[length - 1].getType();
    if (type instanceof PsiEllipsisType ellipsisType) {
      return ellipsisType.toArrayType();
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpressionList args, @NotNull ModPsiUpdater updater) {
    final PsiExpression[] redundantArguments = findRedundantArgument(args.getExpressions(), 
                                                                     myTargetMethod.getParameterList().getParameters(), mySubstitutor);
    if (redundantArguments != null) {
      for (PsiExpression argument : redundantArguments) {
        argument.delete();
      }
    }
  }

  public static void registerIntentions(JavaResolveResult @NotNull [] candidates,
                                        @NotNull PsiExpressionList arguments,
                                        @NotNull Consumer<? super CommonIntentionAction> info) {
    for (JavaResolveResult candidate : candidates) {
      registerIntention(arguments, info, candidate);
    }
  }

  private static void registerIntention(@NotNull PsiExpressionList arguments,
                                        @NotNull Consumer<? super CommonIntentionAction> info,
                                        @NotNull JavaResolveResult candidate) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method == null || !BaseIntentionAction.canModify(arguments)) return;
    if (method.isConstructor() && arguments.getParent() instanceof PsiMethodCallExpression &&
        ((PsiMethodCallExpression)arguments.getParent()).getMethodExpression().textMatches("this") &&
        PsiTreeUtil.isAncestor(method, arguments, true)) {
      // Avoid creating recursive constructor call
      return;
    }
    info.accept(new RemoveRedundantArgumentsFix(method, arguments, substitutor));
  }
}
