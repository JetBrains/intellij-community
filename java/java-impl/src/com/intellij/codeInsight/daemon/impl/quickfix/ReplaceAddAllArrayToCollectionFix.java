// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAddAllArrayToCollectionFix implements IntentionAction {
  private final PsiMethodCallExpression myMethodCall;

  public ReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ReplaceAddAllArrayToCollectionFix(PsiTreeUtil.findSameElementInCopy(myMethodCall, target));
  }

  @Override
  @NotNull
  public String getText() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", myMethodCall.getText(), getCollectionsMethodCall());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!myMethodCall.isValid()) return false;

    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return false;
    final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
    if (jdk == null || !JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_5)) return false;

    final PsiReferenceExpression expression = myMethodCall.getMethodExpression();
    final PsiElement element = expression.resolve();
    if (element instanceof PsiMethod method) {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass collectionsClass = psiFacade.findClass("java.util.Collection", GlobalSearchScope.allScope(project));
      if (collectionsClass != null && InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), collectionsClass, true)) {
        if (Comparing.strEqual(method.getName(), "addAll") && PsiTypes.booleanType().equals(method.getReturnType())) {
          final PsiParameter[] psiParameters = method.getParameterList().getParameters();
          if (psiParameters.length == 1 &&
              psiParameters[0].getType() instanceof PsiClassType &&
              InheritanceUtil.isInheritorOrSelf(((PsiClassType)psiParameters[0].getType()).resolve(), collectionsClass, true)) {
            final PsiExpressionList list = myMethodCall.getArgumentList();
            final PsiExpression[] expressions = list.getExpressions();
            if (expressions.length == 1) {
              if (expressions[0].getType() instanceof PsiArrayType) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getCollectionsMethodCall(), myMethodCall);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(myMethodCall.replace(toReplace));
  }

  @NonNls
  private String getCollectionsMethodCall() {
    final PsiExpression qualifierExpression = myMethodCall.getMethodExpression().getQualifierExpression();
    PsiExpression[] expressions = myMethodCall.getArgumentList().getExpressions();
    return "java.util.Collections.addAll(" +
           (qualifierExpression != null ? qualifierExpression.getText() : "this") +
           ", " + (expressions.length == 0 ? "" : expressions[0].getText()) + ")";
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
