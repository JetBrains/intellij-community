// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class MethodDuplicatesMatchProvider implements MatchProvider {
  private final PsiMethod myMethod;
  private final List<Match> myDuplicates;
  private static final Logger LOG = Logger.getInstance(MethodDuplicatesMatchProvider.class);

  MethodDuplicatesMatchProvider(PsiMethod method, List<Match> duplicates) {
    myMethod = method;
    myDuplicates = duplicates;
  }

  @Override
  public void prepareSignature(Match match) {
    MatchUtil.changeSignature(match, myMethod);
  }

  @Override
  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    final PsiClass containingClass = myMethod.getContainingClass();
    if (isEssentialStaticContextAbsent(match, myMethod)) {
      PsiUtil.setModifierProperty(myMethod, PsiModifier.STATIC, true);
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    final boolean needQualifier = match.getInstanceExpression() != null;
    final boolean needStaticQualifier = isExternal(match, myMethod);
    final boolean nameConflicts = nameConflicts(match);
    final String methodName = myMethod.isConstructor() ? "this" : myMethod.getName();
    @NonNls final String text = needQualifier || needStaticQualifier || nameConflicts
                                ?  "q." + methodName + "()": methodName + "()";
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText(text, null);
    methodCallExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(myMethod.getManager()).reformat(methodCallExpression);
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    for (final PsiParameter parameter : parameters) {
      final List<PsiElement> parameterValue = match.getParameterValues(parameter);
      if (parameterValue != null) {
        for (PsiElement val : parameterValue) {
          methodCallExpression.getArgumentList().add(val);
        }
      }
      else {
        methodCallExpression.getArgumentList().add(factory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(parameter.getType()), parameter));
      }
    }
    if (needQualifier || needStaticQualifier || nameConflicts) {
      final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
      LOG.assertTrue(qualifierExpression != null);
      if (needQualifier) {
        qualifierExpression.replace(match.getInstanceExpression());
      } else if (needStaticQualifier || myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        qualifierExpression.replace(factory.createReferenceExpression(containingClass));
      } else {
        final PsiClass psiClass = PsiTreeUtil.getParentOfType(match.getMatchStart(), PsiClass.class);
        if (psiClass != null && psiClass.isInheritor(containingClass, true)) {
          qualifierExpression.replace(RefactoringChangeUtil.createSuperExpression(containingClass.getManager(), null));
        } else {
          qualifierExpression.replace(RefactoringChangeUtil.createThisExpression(containingClass.getManager(), containingClass));
        }
      }
    }
    VisibilityUtil.escalateVisibility(myMethod, match.getMatchStart());
    final PsiCodeBlock body = myMethod.getBody();
    assert body != null;
    final PsiStatement[] statements = body.getStatements();
    if (statements[statements.length - 1] instanceof PsiReturnStatement) {
      final PsiExpression value = ((PsiReturnStatement)statements[statements.length - 1]).getReturnValue();
      if (value instanceof PsiReferenceExpression) {
        final PsiElement var = ((PsiReferenceExpression)value).resolve();
        if (var instanceof PsiVariable) {
          match.replace(myMethod, methodCallExpression, (PsiVariable)var);
          return methodCallExpression;
        }
      }
    }
    return match.replace(myMethod, methodCallExpression, null);
  }



  private static boolean isExternal(final Match match, final PsiMethod method) {
    final PsiElement matchStart = match.getMatchStart();
    final PsiClass containingClass = method.getContainingClass();
    if (PsiTreeUtil.isAncestor(containingClass, matchStart, false)) {
      return false;
    }
    PsiClass psiClass = PsiTreeUtil.getParentOfType(matchStart, PsiClass.class);
    while (psiClass != null) {
      if (InheritanceUtil.isInheritorOrSelf(psiClass, containingClass, true)) return false;
      psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
    }
    return true;
  }

  private boolean nameConflicts(Match match) {
    PsiClass matchClass = PsiTreeUtil.getParentOfType(match.getMatchStart(), PsiClass.class);
    while (matchClass != null && matchClass != myMethod.getContainingClass()) {
      if (matchClass.findMethodsBySignature(myMethod, false).length > 0) {
        return true;
      }
      matchClass = PsiTreeUtil.getParentOfType(matchClass, PsiClass.class);
    }
    return false;
  }

  public static boolean isEssentialStaticContextAbsent(final Match match, final PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiExpression instanceExpression = match.getInstanceExpression();
      if (instanceExpression != null) return false;
      if (isExternal(match, method)) return true;

      final PsiElement matchStart = match.getMatchStart();
      final PsiClass containingClass = method.getContainingClass();

      if (PsiTreeUtil.isAncestor(containingClass, matchStart, false)) {
        if (CommonJavaRefactoringUtil.isInStaticContext(matchStart, containingClass)) return true;
      }
      else {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(matchStart, PsiClass.class);
        while (psiClass != null) {
          if (InheritanceUtil.isInheritorOrSelf(psiClass, containingClass, true)) {
            if (CommonJavaRefactoringUtil.isInStaticContext(matchStart, psiClass)) return true;
            break;
          }
          psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
        }
      }
    }
    return false;
  }

  @Override
  public List<Match> getDuplicates() {
    return myDuplicates;
  }

  @Override
  public Boolean hasDuplicates() {
    return myDuplicates.isEmpty();
  }

  @Override
  @Nullable
  public String getConfirmDuplicatePrompt(final Match match) {
    final PsiElement matchStart = match.getMatchStart();
    String visibility = VisibilityUtil.getPossibleVisibility(myMethod, matchStart);
    final boolean shouldBeStatic = isEssentialStaticContextAbsent(match, myMethod);
    final String signature = MatchUtil
      .getChangedSignature(match, myMethod, myMethod.hasModifierProperty(PsiModifier.STATIC) || shouldBeStatic, visibility);
    if (signature != null) {
      return JavaRefactoringBundle.message("replace.this.code.fragment.and.change.signature", signature);
    }
    final boolean needToEscalateVisibility = !PsiUtil.isAccessible(myMethod, matchStart, null);
    if (needToEscalateVisibility) {
      final String visibilityPresentation = VisibilityUtil.toPresentableText(visibility);
      return shouldBeStatic
             ? JavaRefactoringBundle.message("replace.this.code.fragment.and.make.method.static.visible", visibilityPresentation)
             : JavaRefactoringBundle.message("replace.this.code.fragment.and.make.method.visible", visibilityPresentation);
    }
    if (shouldBeStatic) {
      return JavaRefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return null;
  }

  @Override
  public String getReplaceDuplicatesTitle(int idx, int size) {
    return JavaRefactoringBundle.message("process.methods.duplicates.title", idx, size, myMethod.getName());
  }
}
