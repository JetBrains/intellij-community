/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

abstract class StaticMembersProcessor<T extends PsiMember & PsiDocCommentOwner> implements Processor<T> {
  private final MultiMap<PsiClass, T> mySuggestions = new LinkedMultiMap<>();

  private final Map<PsiClass, Boolean> myPossibleClasses = new HashMap<>();

  private final PsiElement myPlace;
  private PsiType myExpectedType;

  protected StaticMembersProcessor(PsiElement place) {
    myPlace = place;
    myExpectedType = PsiType.NULL;
  }

  protected abstract boolean isApplicable(T member, PsiElement place);

  @NotNull
  public List<T> getMembersToImport(boolean applicableOnly) {
    final List<T> list = new ArrayList<>();
    final List<T> applicableList = new ArrayList<>();
    for (Map.Entry<PsiClass, Collection<T>> methodEntry : mySuggestions.entrySet()) {
      registerMember(methodEntry.getKey(), methodEntry.getValue(), list, applicableList);
    }

    List<T> result = !applicableOnly && applicableList.isEmpty() ? list : applicableList;
    for (int i = result.size() - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
      T method = result.get(i);
      // check for manually excluded
      if (StaticImportMethodFix.isExcluded(method)) {
        result.remove(i);
      }
    }
    Collections.sort(result, CodeInsightUtil.createSortIdenticalNamedMembersComparator(myPlace));
    return result;
  }

  public PsiType getExpectedType() {
    if (myExpectedType == PsiType.NULL) {
      myExpectedType = getExpectedTypeInternal();
    }
    return myExpectedType;
  }

  private PsiType getExpectedTypeInternal() {
    if (myPlace == null) return null;
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(myPlace.getParent());

    if (parent instanceof PsiVariable) {
      if (myPlace.equals(PsiUtil.skipParenthesizedExprDown(((PsiVariable)parent).getInitializer()))) {
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (myPlace.equals(PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getRExpression()))) {
        return ((PsiAssignmentExpression)parent).getLExpression().getType();
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiElement psiElement = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, PsiMethod.class);
      if (psiElement instanceof PsiLambdaExpression) {
        return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)psiElement).getFunctionalInterfaceType());
      }
      else if (psiElement instanceof PsiMethod) {
        return ((PsiMethod)psiElement).getReturnType();
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement pParent = parent.getParent();
      if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
        final JavaResolveResult resolveResult = ((PsiCallExpression)pParent).resolveMethodGenerics();
        final PsiElement psiElement = resolveResult.getElement();
        if (psiElement instanceof PsiMethod) {
          final PsiMethod psiMethod = (PsiMethod)psiElement;
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          final int idx = ArrayUtilRt.find(((PsiExpressionList)parent).getExpressions(), PsiUtil.skipParenthesizedExprUp(myPlace));
          if (idx > -1 && parameters.length > 0) {
            PsiType parameterType = parameters[Math.min(idx, parameters.length - 1)].getType();
            if (idx >= parameters.length - 1) {
              final PsiParameter lastParameter = parameters[parameters.length - 1];
              if (lastParameter.isVarArgs()) {
                parameterType = ((PsiEllipsisType)lastParameter.getType()).getComponentType();
              }
            }
            return resolveResult.getSubstitutor().substitute(parameterType);
          }
          else {
            return null;
          }
        }
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    }
    return null;
  }

  @Override
  public boolean process(T member) {
    ProgressManager.checkCanceled();
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass != null) {
      final String qualifiedName = containingClass.getQualifiedName();
      final PsiFile containingFile = myPlace.getContainingFile();
      if (qualifiedName != null && containingFile != null && !ImportFilter.shouldImport(containingFile, qualifiedName)) {
        return true;
      }
    }
    PsiFile file = member.getContainingFile();
    if (file instanceof PsiJavaFile
        //do not show methods from default package
        && !((PsiJavaFile)file).getPackageName().isEmpty()) {
      mySuggestions.putValue(containingClass, member);
    }
    return processCondition();
  }

  private boolean processCondition() {
    return mySuggestions.size() < 50;
  }

  private void registerMember(PsiClass containingClass,
                              Collection<T> members,
                              List<T> list,
                              List<T> applicableList) {
    Boolean alreadyMentioned = myPossibleClasses.get(containingClass);
    if (alreadyMentioned == Boolean.TRUE) return;
    if (containingClass.getQualifiedName() == null) {
      return;
    }
    if (alreadyMentioned == null) {
      myPossibleClasses.put(containingClass, false);
    }
    for (T member : members) {
      if (JavaCompletionUtil.isInExcludedPackage(member, false) || !member.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }

      if (alreadyMentioned == null) {
        list.add(member);
        alreadyMentioned = Boolean.FALSE;
      }

      if (!PsiUtil.isAccessible(myPlace.getProject(), member, myPlace, containingClass)) {
        continue;
      }
      if (isApplicable(member, myPlace)) {
        applicableList.add(member);
        myPossibleClasses.put(containingClass, true);
        break;
      }
    }
  }
}
