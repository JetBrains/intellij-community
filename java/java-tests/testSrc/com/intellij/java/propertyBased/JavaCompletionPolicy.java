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

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.propertyBased.CompletionPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author peter
 */
class JavaCompletionPolicy extends CompletionPolicy {
  @Nullable
  @Override
  protected String getExpectedVariant(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement leaf, @Nullable PsiReference ref) {
    if (isBuggyInjection(file)) return null;

    return super.getExpectedVariant(editor, file, leaf, ref);
  }

  // a language where there are bugs in completion which maintainers of this Java-specific test can't or don't want to fix
  private static boolean isBuggyInjection(@NotNull PsiFile file) {
    return Arrays.asList("XML", "HTML", "PointcutExpression", "HQL").contains(file.getLanguage().getID());
  }

  @Override
  protected boolean shouldSuggestReferenceText(@NotNull PsiReference ref, @NotNull PsiElement target) {
    PsiElement refElement = ref.getElement();
    if (refElement.getContainingFile().getLanguage().getID().equals("GWT JavaScript")) {
      // for GWT class members refs like "MyClass::mmm(I)(1)", lookup items are only a subset of possible reference texts
      return false;
    }

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
    return super.shouldSuggestReferenceText(ref, target);
  }

  private static boolean shouldSuggestJavaTarget(PsiJavaCodeReferenceElement ref, @NotNull PsiElement target) {
    if (PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class) != null) return false;

    PsiAnnotation anno = PsiTreeUtil.getParentOfType(ref, PsiAnnotation.class);
    if (!ref.isQualified() && target instanceof PsiPackage) return false;
    if (target instanceof PsiClass) {
      if (anno != null && !((PsiClass)target).isAnnotationType()) {
        if (PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), ref, true)) {
          return false; // inner annotation
        }
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
           ((PsiModifierListOwner)target).hasModifierProperty(PsiModifier.STATIC) &&
           qualifier instanceof PsiJavaCodeReferenceElement &&
           !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass);
  }

  @Override
  protected boolean shouldSuggestNonReferenceLeafText(@NotNull PsiElement leaf) {
    PsiElement parent = leaf.getParent();
    if (leaf instanceof PsiKeyword) {
      if (parent instanceof PsiExpression && hasUnresolvedRefsBefore(leaf, parent)) {
        return false;
      }
    }
    if (leaf.textMatches(PsiKeyword.TRUE) || leaf.textMatches(PsiKeyword.FALSE)) {
      return false; // boolean literal presence depends on expected types, which can be missing in red files
    }
    return true;
  }

  private static boolean hasUnresolvedRefsBefore(PsiElement leaf, PsiElement parent) {
    return SyntaxTraverser.psiTraverser(parent)
                          .filter(PsiJavaCodeReferenceElement.class)
                          .filter(r -> r.getTextRange().getEndOffset() < leaf.getTextRange().getEndOffset() && r.resolve() == null)
                          .isNotEmpty();
  }
}
