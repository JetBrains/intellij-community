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
package com.intellij.refactoring.util.duplicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* User: anna
* Date: 1/16/12
*/
class MethodDuplicatesMatchProvider implements MatchProvider {
  private final PsiMethod myMethod;
  private final List<Match> myDuplicates;
  private static final Logger LOG = Logger.getInstance("#" + MethodDuplicatesMatchProvider.class.getName());

  MethodDuplicatesMatchProvider(PsiMethod method, List<Match> duplicates) {
    myMethod = method;
    myDuplicates = duplicates;
  }

  @Override
  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    MatchUtil.changeSignature(match, myMethod);
    final PsiClass containingClass = myMethod.getContainingClass();
    if (isEssentialStaticContextAbsent(match)) {
      PsiUtil.setModifierProperty(myMethod, PsiModifier.STATIC, true);
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
    final boolean needQualifier = match.getInstanceExpression() != null;
    final boolean needStaticQualifier = isExternal(match);
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



  private boolean isExternal(final Match match) {
    final PsiElement matchStart = match.getMatchStart();
    final PsiClass containingClass = myMethod.getContainingClass();
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

  private boolean isEssentialStaticContextAbsent(final Match match) {
    if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiExpression instanceExpression = match.getInstanceExpression();
      if (instanceExpression != null) return false;
      if (isExternal(match)) return true;

      final PsiElement matchStart = match.getMatchStart();
      final PsiClass containingClass = myMethod.getContainingClass();

      if (PsiTreeUtil.isAncestor(containingClass, matchStart, false)) {
        if (RefactoringUtil.isInStaticContext(matchStart, containingClass)) return true;
      }
      else {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(matchStart, PsiClass.class);
        while (psiClass != null) {
          if (InheritanceUtil.isInheritorOrSelf(psiClass, containingClass, true)) {
            if (RefactoringUtil.isInStaticContext(matchStart, psiClass)) return true;
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
    final boolean shouldBeStatic = isEssentialStaticContextAbsent(match);
    final String signature = MatchUtil
      .getChangedSignature(match, myMethod, myMethod.hasModifierProperty(PsiModifier.STATIC) || shouldBeStatic, visibility);
    if (signature != null) {
      return RefactoringBundle.message("replace.this.code.fragment.and.change.signature", signature);
    }
    final boolean needToEscalateVisibility = !PsiUtil.isAccessible(myMethod, matchStart, null);
    if (needToEscalateVisibility) {
      final String visibilityPresentation = VisibilityUtil.toPresentableText(visibility);
      return shouldBeStatic
             ? RefactoringBundle.message("replace.this.code.fragment.and.make.method.static.visible", visibilityPresentation)
             : RefactoringBundle.message("replace.this.code.fragment.and.make.method.visible", visibilityPresentation);
    }
    if (shouldBeStatic) {
      return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return null;
  }

  @Override
  public String getReplaceDuplicatesTitle(int idx, int size) {
    return RefactoringBundle.message("process.methods.duplicates.title", idx, size, myMethod.getName());
  }
}
