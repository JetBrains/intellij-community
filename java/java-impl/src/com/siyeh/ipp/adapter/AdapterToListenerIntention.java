// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.adapter;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class AdapterToListenerIntention extends MCIntention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AdapterToListenerPredicate();
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    final String text = element.getText();
    return IntentionPowerPackBundle.message("adapter.to.listener.intention.name", text);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return IntentionPowerPackBundle.message("adapter.to.listener.intention.family.name");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiClass aClass)) {
      return;
    }
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList == null) {
      return;
    }
    final PsiJavaCodeReferenceElement[] extendsReferences =
      extendsList.getReferenceElements();
    if (extendsReferences.length != 1) {
      return;
    }
    final PsiJavaCodeReferenceElement extendsReference =
      extendsReferences[0];
    final PsiElement target = extendsReference.resolve();
    if (!(target instanceof PsiClass extendsClass)) {
      return;
    }
    final PsiReferenceList implementsList =
      extendsClass.getImplementsList();
    if (implementsList == null) {
      return;
    }
    final PsiJavaCodeReferenceElement[] implementsReferences =
      implementsList.getReferenceElements();
    final List<PsiJavaCodeReferenceElement> listenerReferences = new ArrayList<>();
    for (PsiJavaCodeReferenceElement implementsReference :
      implementsReferences) {
      final String name = implementsReference.getReferenceName();
      if (name != null && !name.endsWith("Listener")) {
        continue;
      }
      final PsiElement implementsTarget = implementsReference.resolve();
      if (!(implementsTarget instanceof PsiClass implementsClass)) {
        continue;
      }
      if (!implementsClass.isInterface()) {
        continue;
      }
      final PsiMethod[] methods = implementsClass.getMethods();
      for (PsiMethod method : methods) {
        final PsiMethod overridingMethod =
          aClass.findMethodBySignature(method, false);
        if (overridingMethod == null) {
          implementMethodInClass(method, aClass);
          continue;
        }
        final PsiMethod[] superMethods =
          overridingMethod.findSuperMethods(implementsClass);
        for (PsiMethod superMethod : superMethods) {
          if (!superMethod.equals(method)) {
            continue;
          }
          removeCallsToSuperMethodFromMethod(overridingMethod,
                                             extendsClass);
        }
      }
      listenerReferences.add(implementsReference);
    }
    extendsReference.delete();
    final PsiReferenceList referenceList = aClass.getImplementsList();
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement listenerReference :
        listenerReferences) {
        referenceList.add(listenerReference);
      }
    }
  }

  private static void removeCallsToSuperMethodFromMethod(
    @NotNull PsiMethod overridingMethod,
    @NotNull PsiClass superClass) {
    final PsiCodeBlock body = overridingMethod.getBody();
    if (body == null) {
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    for (PsiStatement statement : statements) {
      if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
        continue;
      }
      final PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        continue;
      }
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiSuperExpression)) {
        continue;
      }
      final PsiMethod targetMethod =
        methodCallExpression.resolveMethod();
      if (targetMethod == null) {
        continue;
      }
      final PsiClass containingClass = targetMethod.getContainingClass();
      if (!superClass.equals(containingClass)) {
        continue;
      }
      statement.delete();
    }
  }

  private static void implementMethodInClass(@NotNull PsiMethod method,
                                             @NotNull PsiClass aClass) {
    final PsiMethod newMethod = (PsiMethod)aClass.add(method);
    final PsiDocComment comment = newMethod.getDocComment();
    if (comment != null) {
      comment.delete();
    }
    final PsiModifierList modifierList = newMethod.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    final Project project = aClass.getProject();
    final JavaCodeStyleSettings codeStyleSettings = JavaCodeStyleSettings.getInstance(aClass.getContainingFile());
    if (codeStyleSettings.INSERT_OVERRIDE_ANNOTATION &&
        PsiUtil.isLanguageLevel6OrHigher(aClass)) {
      modifierList.addAnnotation("java.lang.Override");
    }
    final PsiElementFactory factory =
      JavaPsiFacade.getElementFactory(project);
    final PsiCodeBlock codeBlock = factory.createCodeBlock();
    newMethod.add(codeBlock);
  }
}