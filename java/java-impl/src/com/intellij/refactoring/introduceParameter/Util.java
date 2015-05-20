/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 14:38:40
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Util {

  public static void analyzeExpression(PsiExpression expr,
                                       List<UsageInfo> localVars,
                                       List<UsageInfo> classMemberRefs,
                                       List<UsageInfo> params) {

    if (expr instanceof PsiThisExpression || expr instanceof PsiSuperExpression) {
      classMemberRefs.add(new ClassMemberInExprUsageInfo(expr));
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)expr;

      PsiElement subj = refExpr.resolve();

      if (subj instanceof PsiParameter) {
        params.add(new ParameterInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiLocalVariable) {
        localVars.add(new LocalVariableInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiField || subj instanceof PsiMethod) {
        classMemberRefs.add(new ClassMemberInExprUsageInfo(refExpr));
      }

    }

    PsiElement[] children = expr.getChildren();

    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        analyzeExpression((PsiExpression)child, localVars, classMemberRefs, params);
      }
    }
  }

  @NotNull
  private static PsiElement getPhysical(@NotNull PsiElement expr) {
    PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);
    if (physicalElement != null) expr = physicalElement;
    return expr;
  }

  public static PsiMethod getContainingMethod(PsiElement expr) {
    return PsiTreeUtil.getParentOfType(getPhysical(expr), PsiMethod.class);
  }
  public static boolean isAncestor(PsiElement ancestor, PsiElement element, boolean strict) {
    return PsiTreeUtil.isAncestor(getPhysical(ancestor), getPhysical(element), strict);
  }

  public static boolean anyFieldsWithGettersPresent(List<UsageInfo> classMemberRefs) {
    for (UsageInfo usageInfo : classMemberRefs) {

      if (usageInfo.getElement() instanceof PsiReferenceExpression) {
        PsiElement e = ((PsiReferenceExpression)usageInfo.getElement()).resolve();

        if (e instanceof PsiField) {
          PsiField psiField = (PsiField)e;
          PsiMethod getterPrototype = GenerateMembersUtil.generateGetterPrototype(psiField);

          PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

          if (getter != null) return true;
        }
      }
    }

    return false;
  }

  // returns parameters that are used solely in specified expression
  @NotNull
  public static TIntArrayList findParametersToRemove(@NotNull PsiMethod method,
                                                     @NotNull final PsiExpression expr,
                                                     @Nullable final PsiExpression[] occurences) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) return new TIntArrayList();

    PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY);
    final PsiMethod[] allMethods = ArrayUtil.append(overridingMethods, method);

    final TIntHashSet suspects = new TIntHashSet();
    expr.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiParameter) {
          int i = ArrayUtil.find(parameters, resolved);
          if (i != -1) {
            suspects.add(i);
          }
        }
      }
    });

    final TIntIterator iterator = suspects.iterator();
    while(iterator.hasNext()) {
      final int paramNum = iterator.next();
      for (PsiMethod psiMethod : allMethods) {
        PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
        if (paramNum >= psiParameters.length) continue;
        PsiParameter parameter = psiParameters[paramNum];
        if (!ReferencesSearch.search(parameter, parameter.getResolveScope(), false).forEach(new Processor<PsiReference>() {
          public boolean process(final PsiReference reference) {
            PsiElement element = reference.getElement();
            boolean stillCanBeRemoved = false;
            if (element != null) {
              stillCanBeRemoved = isAncestor(expr, element, false) || PsiUtil.isInsideJavadocComment(getPhysical(element));
              if (!stillCanBeRemoved && occurences != null) {
                for (PsiExpression occurence : occurences) {
                  if (isAncestor(occurence, element, false)) {
                    stillCanBeRemoved = true;
                    break;
                  }
                }
              }
            }
            if (!stillCanBeRemoved) {
              iterator.remove();
              return false;
            }
           return true;
          }
        })) break;
      }
    }

    return new TIntArrayList(suspects.toArray());
  }
}
