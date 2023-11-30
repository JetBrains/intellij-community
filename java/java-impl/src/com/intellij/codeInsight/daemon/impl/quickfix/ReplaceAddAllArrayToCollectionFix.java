// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAddAllArrayToCollectionFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  public ReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", "Collections.addAll()");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(context.file());
    if (module == null) return null;
    final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
    if (jdk == null || !JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_5)) return null;

    final PsiReferenceExpression expression = call.getMethodExpression();
    final PsiElement element = expression.resolve();
    if (element instanceof PsiMethod method) {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(context.project());
      final PsiClass collectionsClass = psiFacade.findClass("java.util.Collection", GlobalSearchScope.allScope(context.project()));
      if (collectionsClass != null && InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), collectionsClass, true)) {
        if (Comparing.strEqual(method.getName(), "addAll") && PsiTypes.booleanType().equals(method.getReturnType())) {
          final PsiParameter[] psiParameters = method.getParameterList().getParameters();
          if (psiParameters.length == 1 &&
              psiParameters[0].getType() instanceof PsiClassType &&
              InheritanceUtil.isInheritorOrSelf(((PsiClassType)psiParameters[0].getType()).resolve(), collectionsClass, true)) {
            final PsiExpressionList list = call.getArgumentList();
            final PsiExpression[] expressions = list.getExpressions();
            if (expressions.length == 1) {
              if (expressions[0].getType() instanceof PsiArrayType) {
                return Presentation.of(CommonQuickFixBundle.message("fix.replace.x.with.y", call.getText(), getCollectionsMethodCall(call)));
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.project());
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getCollectionsMethodCall(call), call);
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(call.replace(toReplace));
  }

  @NonNls
  private static String getCollectionsMethodCall(@NotNull PsiMethodCallExpression call) {
    final PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    return "java.util.Collections.addAll(" +
           (qualifierExpression != null ? qualifierExpression.getText() : "this") +
           ", " + (expressions.length == 0 ? "" : expressions[0].getText()) + ")";
  }
}
