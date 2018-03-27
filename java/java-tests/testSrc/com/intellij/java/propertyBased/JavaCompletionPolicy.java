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
    return super.isAfterError(file, leaf) || isAdoptedOrphanPsiAfterClassEnd(leaf) || isInsideAnnotationWithErrors(leaf);
  }

  private static boolean isInsideAnnotationWithErrors(PsiElement leaf) {
    PsiAnnotationParameterList list = PsiTreeUtil.getParentOfType(leaf, PsiAnnotationParameterList.class);
    return list != null && PsiTreeUtil.findChildOfType(list, PsiErrorElement.class) != null;
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

    PsiAnnotation anno = PsiTreeUtil.getParentOfType(ref, PsiAnnotation.class);
    if (!ref.isQualified() && target instanceof PsiPackage) return false;
    if (target instanceof PsiClass) {
      if (isCyclicInheritance(ref, target)) return false;
      if (anno != null && !((PsiClass)target).isAnnotationType()) {
        if (ref.getParent() == anno) {
          return false; // red code
        }
        if (PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), ref, true)) {
          return false; // inner annotation
        }
      }
    }
    if (target instanceof PsiVariable && PsiTreeUtil.isAncestor(target, ref, false)) {
      return false; // non-initialized variable
    }
    if (target instanceof PsiField &&
        SyntaxTraverser.psiApi().parents(ref).find(e -> e instanceof PsiMethod && ((PsiMethod)e).isConstructor()) != null) {
      // https://youtrack.jetbrains.com/issue/IDEA-174744 on red code
      return false;
    }
    if (anno != null) {
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
           ((PsiModifierListOwner)target).hasModifierProperty(PsiModifier.STATIC) &&
           qualifier instanceof PsiJavaCodeReferenceElement &&
           !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass);
  }

  @Override
  protected boolean shouldSuggestNonReferenceLeafText(@NotNull PsiElement leaf) {
    PsiElement parent = leaf.getParent();
    if (leaf instanceof PsiKeyword) {
      if (parent instanceof PsiClassObjectAccessExpression &&
          PsiUtil.resolveClassInType(((PsiClassObjectAccessExpression)parent).getOperand().getType()) == null) {
        return false;
      }
      if (parent instanceof PsiModifierList) {
        if (Arrays.stream(parent.getNode().getChildren(null)).filter(e -> leaf.textMatches(e.getText())).count() > 1) {
          return false;
        }
        if (parent.getParent() instanceof PsiModifierListOwner && PsiTreeUtil.getParentOfType(parent.getParent(), PsiCodeBlock.class, true, PsiClass.class) != null) {
          return false; // no modifiers for local classes/variables
        }
      }
    }
    if (leaf.textMatches(PsiKeyword.TRUE) || leaf.textMatches(PsiKeyword.FALSE)) {
      return false; // boolean literal presence depends on expected types, which can be missing in red files
    }
    if (parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null) {
      PsiJavaCodeReferenceElement reference = ((PsiNewExpression)parent).getClassOrAnonymousClassReference();
      if (reference == null || reference.resolve() == null) {
        return false; // this.new Foo isn't completed when there's no "Foo"
      }
    }
    if (leaf.textMatches(PsiKeyword.IMPLEMENTS)) {
      PsiClass cls = PsiTreeUtil.getParentOfType(leaf, PsiClass.class);
      if (cls == null || cls.isInterface()) {
        return false;
      }
    }
    return true;
  }

}
