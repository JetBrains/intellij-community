// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;

public class WrapWithUnmodifiableAction extends BaseIntentionAction {
  private static final String JAVA_UTIL_SORTED_MAP = "java.util.SortedMap";

  private static final Map<String, String> CLASS_TO_METHOD = Map.of(
              JAVA_UTIL_LIST, "unmodifiableList",
              JAVA_UTIL_SORTED_SET, "unmodifiableSortedSet",
              JAVA_UTIL_SET, "unmodifiableSet",
              JAVA_UTIL_SORTED_MAP, "unmodifiableSortedMap",
              JAVA_UTIL_MAP, "unmodifiableMap"
  );

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (editor == null || file == null || !canModify(file)) {
      return;
    }
    PsiExpression expression = getParentExpression(editor, file);
    if (expression != null) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (psiClass != null) {

        PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(getExpectedType(expression));
        if (expectedClass != null) {
          String collectionClass = findSuperClass(psiClass, expectedClass, CLASS_TO_METHOD.keySet());
          if (collectionClass != null) {
            String method = CLASS_TO_METHOD.get(collectionClass);
            wrapWith(expression, method);
          }
        }
      }
    }
  }

  @Nullable
  private static PsiExpression getParentExpression(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiExpression expression = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
    if (expression == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset() - 1);
      expression = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
    }
    if (expression != null) {
      PsiMethodCallExpression methodCall = tryCast(expression.getParent(), PsiMethodCallExpression.class);
      if (methodCall != null && methodCall.getMethodExpression() == expression) {
        return methodCall;
      }
    }
    return expression;
  }

  private static void wrapWith(PsiExpression expression, String methodName) {
    CommentTracker tracker = new CommentTracker();
    String text = JAVA_UTIL_COLLECTIONS + '.' + methodName + '(' + tracker.text(expression) + ')';
    PsiElement result = tracker.replaceAndRestoreComments(expression, text);
    JavaCodeStyleManager.getInstance(result.getProject()).shortenClassReferences(result);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (editor == null || file == null || !canModify(file)) {
      return false;
    }
    PsiExpression expression = getParentExpression(editor, file);
    if (expression != null) {
      if (PsiUtil.isOnAssignmentLeftHand(expression)) return false;
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (psiClass != null) {
        PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(getExpectedType(expression));

        if (expectedClass != null) {
          String collectionClass = findSuperClass(psiClass, expectedClass, CLASS_TO_METHOD.keySet());
          if (collectionClass != null && !isUnmodifiable(expression)) {
            String message = switch (collectionClass) {
              case JAVA_UTIL_LIST -> "intention.wrap.with.unmodifiable.list";
              case JAVA_UTIL_SET, JAVA_UTIL_SORTED_SET -> "intention.wrap.with.unmodifiable.set";
              default -> "intention.wrap.with.unmodifiable.map";
            };
            setText(JavaBundle.message(message));
            return true;
          }
        }
      }
    }
    return false;
  }

  private static PsiType getExpectedType(@NotNull PsiExpression expression) {
    PsiType expectedType = PsiTypesUtil.getExpectedTypeByParent(expression); // try the cheaper way first
    if (expectedType != null) {
      return expectedType;
    }
    return ExpectedTypeUtils.findExpectedType(expression, false);
  }

  private static String findSuperClass(PsiClass psiClass,
                                       PsiClass expectedClass,
                                       Set<String> candidates) {
    var processor = new Processor<PsiClass>() {
      String myResult;

      @Override
      public boolean process(PsiClass superClass) {
        String qualifiedName = superClass.getQualifiedName();
        if (qualifiedName != null && candidates.contains(qualifiedName)) {
          if (InheritanceUtil.isInheritorOrSelf(superClass, expectedClass, true)) {
            myResult = qualifiedName;
          }
          return false;
        }
        return true;
      }
    };
    InheritanceUtil.processSupers(psiClass, true, processor);
    return processor.myResult;
  }

  private static boolean isUnmodifiable(@NotNull PsiExpression expression) {
    DfType dfType = CommonDataflow.getDfType(expression);
    if (Mutability.fromDfType(dfType).isUnmodifiable()) {
      return true;
    }
    PsiMethodCallExpression methodCall = tryCast(expression, PsiMethodCallExpression.class);
    if (isUnmodifiableCall(methodCall)) {
      return true;
    }

    PsiExpressionList expressionList = tryCast(ExpressionUtils.getPassThroughParent(expression), PsiExpressionList.class);
    if (expressionList != null && expressionList.getExpressionCount() == 1) {
      methodCall = tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (isUnmodifiableCall(methodCall)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isUnmodifiableCall(@Nullable PsiMethodCallExpression methodCall) {
    if (methodCall != null) {
      String name = methodCall.getMethodExpression().getReferenceName();
      if (name != null && name.startsWith("unmodifiable")) {
        PsiMethod method = methodCall.resolveMethod();
        if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass psiClass = method.getContainingClass();
          if (psiClass != null && JAVA_UTIL_COLLECTIONS.equals(psiClass.getQualifiedName())) {
            return true;
          }
        }
      }
    }
    return false;
  }


  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.wrap.with.unmodifiable");
  }
}
