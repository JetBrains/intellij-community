// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToVarargsMethodFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.to.variable.arity.method.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element.getParent() instanceof PsiMethod method)) {
      return;
    }
    final Collection<PsiReferenceExpression> methodCalls = new ArrayList<>();
    final Collection<PsiDocMethodOrFieldRef> javadocRefs = new ArrayList<>();
    for (PsiReference reference : ReferencesSearch.search(method).findAll()) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiReferenceExpression ref) {
        methodCalls.add(updater.getWritable(ref));
      }
      else if (referenceElement instanceof PsiDocMethodOrFieldRef ref) {
        javadocRefs.add(updater.getWritable(ref));
      }
    }
    method = updater.getWritable(method);
    makeMethodVarargs(method);
    makeMethodCallsVarargs(methodCalls);
    makeJavadocRefsVarargs(javadocRefs);
  }

  private static void makeJavadocRefsVarargs(Collection<PsiDocMethodOrFieldRef> refs) {
    for (PsiDocMethodOrFieldRef ref : refs) {
      final String[] signature = ref.getSignature();
      if (signature == null) return;
      final PsiElement name = ref.getNameElement();
      if (name == null) return;
      String last = signature[signature.length - 1];
      if (!last.endsWith("[]")) return;
      last = last.substring(0, last.length() - 2) + "...";

      final StringBuilder text = new StringBuilder();
      text.append("/** {@link #").append(name.getText()).append("(");
      for (int i = 0; i < signature.length - 1; i++) {
        text.append(signature[i]).append(",");
      }
      text.append(last).append(")} */");
      PsiComment comment = JavaPsiFacade.getElementFactory(ref.getProject()).createCommentFromText(text.toString(), ref);
      ref.replace(comment.getChildren()[2].getChildren()[3]);
    }
  }

  private static void makeMethodVarargs(PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter lastParameter = parameterList.getParameter(parameterList.getParametersCount() - 1);
    if (lastParameter == null) {
      return;
    }
    lastParameter.normalizeDeclaration();
    final PsiType type = lastParameter.getType();
    if (!(type instanceof PsiArrayType arrayType)) {
      return;
    }
    final PsiType componentType = arrayType.getComponentType();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    final PsiType ellipsisType = new PsiEllipsisType(componentType, TypeAnnotationProvider.Static.create(type.getAnnotations()));
    final PsiTypeElement newTypeElement = factory.createTypeElement(ellipsisType);
    final PsiTypeElement typeElement = lastParameter.getTypeElement();
    if (typeElement != null) {
      CommentTracker ct = new CommentTracker();
      PsiElement result = ct.replace(typeElement, newTypeElement);
      ct.insertCommentsBefore(result.getLastChild()); // Swap comments, example: String /* Foo */ [] -> String/* Foo */...
      JavaCodeStyleManager.getInstance(method.getProject()).shortenClassReferences(result);
    }
  }

  private static void makeMethodCallsVarargs(Collection<PsiReferenceExpression> referenceExpressions) {
    for (PsiReferenceExpression referenceExpression : referenceExpressions) {
      if (!(referenceExpression.getParent() instanceof PsiMethodCallExpression methodCallExpression)) {
        continue;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        continue;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      if (!(lastArgument instanceof PsiNewExpression newExpression)) {
        continue;
      }
      final PsiArrayInitializerExpression arrayInitializerExpression = newExpression.getArrayInitializer();
      if (arrayInitializerExpression == null) {
        continue;
      }
      for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
        argumentList.add(initializer);
      }
      lastArgument.delete();
    }
  }
}
