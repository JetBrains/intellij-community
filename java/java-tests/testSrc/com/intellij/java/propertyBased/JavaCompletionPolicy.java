/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.propertyBased;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.propertyBased.CompletionPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author peter
 */
class JavaCompletionPolicy extends CompletionPolicy {
  @Override
  protected boolean shouldSuggestReferenceText(@NotNull PsiReference ref, @NotNull PsiElement target) {
    PsiElement refElement = ref.getElement();
    if (refElement instanceof PsiJavaCodeReferenceElement && 
        !shouldSuggestJavaTarget((PsiJavaCodeReferenceElement)refElement, target)) {
      return false;
    }
    return true;
  }

  private static boolean shouldSuggestJavaTarget(PsiJavaCodeReferenceElement ref, @NotNull PsiElement target) {
    if (PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class) != null) return false;

    if (!ref.isQualified() && target instanceof PsiPackage) return false;
    if (target instanceof PsiClass && PsiTreeUtil.isAncestor(target, ref, true)) {
      PsiElement lBrace = ((PsiClass)target).getLBrace();
      if (lBrace == null || ref.getTextRange().getStartOffset() < lBrace.getTextRange().getStartOffset()) {
        return false;
      }
    }
    if (target instanceof PsiField &&
        SyntaxTraverser.psiApi().parents(ref).find(e -> e instanceof PsiMethod && ((PsiMethod)e).isConstructor()) != null) {
      // https://youtrack.jetbrains.com/issue/IDEA-174744 on red code
      return false;
    }
    if (isStaticWithInstanceQualifier(ref, target)) {
      return false;
    }
    return target != null;
  }

  private static boolean isStaticWithInstanceQualifier(PsiJavaCodeReferenceElement ref, @NotNull PsiElement target) {
    PsiElement qualifier = ref.getQualifier();
    return target instanceof PsiModifierListOwner &&
           ((PsiModifierListOwner)target).hasModifier(JvmModifier.STATIC) &&
           qualifier instanceof PsiJavaCodeReferenceElement &&
           !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass);
  }

  @Override
  protected boolean shouldSuggestNonReferenceLeafText(@NotNull PsiElement leaf) {
    if (leaf instanceof PsiKeyword) {
      if (leaf.getParent() instanceof PsiClassObjectAccessExpression &&
          PsiUtil.resolveClassInType(((PsiClassObjectAccessExpression)leaf.getParent()).getOperand().getType()) == null) {
        return false;
      }
      if (leaf.getParent() instanceof PsiModifierList &&
          Arrays.stream(leaf.getParent().getNode().getChildren(null)).filter(e -> leaf.textMatches(e.getText())).count() > 1) {
        return false;
      }
    }
    if (leaf.textMatches(PsiKeyword.TRUE) || leaf.textMatches(PsiKeyword.FALSE)) {
      return false; // boolean literal presence depends on expected types, which can be missing in red files
    }
    return true;
  }

}
