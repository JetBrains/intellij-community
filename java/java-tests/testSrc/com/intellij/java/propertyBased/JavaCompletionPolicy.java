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

import com.intellij.lang.ASTNode;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.propertyBased.CompletionPolicy;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author peter
 */
class JavaCompletionPolicy extends CompletionPolicy {

  @Override
  protected boolean isAfterError(@NotNull PsiFile file, @NotNull PsiElement leaf) {
    return super.isAfterError(file, leaf) || isAdoptedOrphanPsiAfterClassEnd(leaf);
  }

  @Override
  protected boolean shouldSuggestReferenceText(@NotNull PsiReference ref, @NotNull PsiElement target) {
    PsiElement refElement = ref.getElement();
    if (refElement instanceof PsiJavaCodeReferenceElement && 
        !shouldSuggestJavaTarget((PsiJavaCodeReferenceElement)refElement, target)) {
      return false;
    }
    if (ref instanceof FileReference) {
      if (target instanceof PsiFile) {
        return false; // IDEA-177167
      }
      if (target instanceof PsiDirectory && ((PsiDirectory)target).getName().endsWith(".jar") && ((PsiDirectory)target).getParentDirectory() == null) {
        return false; // IDEA-178629
      }
    }
    return true;
  }

  private static boolean isAdoptedOrphanPsiAfterClassEnd(PsiElement element) {
    PsiClass topmostClass = PsiTreeUtil.getTopmostParentOfType(element, PsiClass.class);
    if (topmostClass != null) {
      ASTNode rBrace = topmostClass.getNode().findChildByType(JavaTokenType.RBRACE); 
      // not PsiClass#getRBrace, because we need the first one, and invalid classes can contain several '}'
      if (rBrace != null && rBrace.getTextRange().getStartOffset() < element.getTextRange().getStartOffset()) return true;
    }
    return false;
  }

  private static boolean shouldSuggestJavaTarget(PsiJavaCodeReferenceElement ref, @NotNull PsiElement target) {
    if (PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class) != null) return false;

    if (!ref.isQualified() && target instanceof PsiPackage) return false;
    if (target instanceof PsiClass) {
      if (isCyclicInheritance(ref, target)) return false;
      if (ref.getParent() instanceof PsiAnnotation && !((PsiClass)target).isAnnotationType()) {
        return false; // red code
      }
    }
    if (target instanceof PsiField &&
        SyntaxTraverser.psiApi().parents(ref).find(e -> e instanceof PsiMethod && ((PsiMethod)e).isConstructor()) != null) {
      // https://youtrack.jetbrains.com/issue/IDEA-174744 on red code
      return false;
    }
    if (PsiTreeUtil.getParentOfType(ref, PsiAnnotation.class) != null) {
      if (target instanceof PsiMethod || target instanceof PsiField && !ExpressionUtils.isConstant((PsiField)target)) {
        return false; // red code;
      }
    }
    if (isStaticWithInstanceQualifier(ref, target)) {
      return false;
    }
    return target != null;
  }

  private static boolean isCyclicInheritance(PsiJavaCodeReferenceElement ref, @NotNull PsiElement target) {
    if (PsiTreeUtil.isAncestor(target, ref, true)) {
      PsiElement lBrace = ((PsiClass)target).getLBrace();
      return lBrace == null || ref.getTextRange().getStartOffset() < lBrace.getTextRange().getStartOffset();
    }
    return false;
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
