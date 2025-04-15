// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.propertyBased.CompletionPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

class JavaCompletionPolicy extends CompletionPolicy {
  @Nullable
  @Override
  protected String getExpectedVariant(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement leaf, @Nullable PsiReference ref) {
    if (isBuggyInjection(file)) return null;

    return super.getExpectedVariant(editor, file, leaf, ref);
  }

  @Override
  public boolean areDuplicatesOk(@NotNull LookupElement item1, @NotNull LookupElement item2) {
    return areSameNamedFieldsInSameClass(item1.getObject(), item2.getObject()) || super.areDuplicatesOk(item1, item2);
  }

  private static boolean areSameNamedFieldsInSameClass(Object o1, Object o2) {
    if (o1 instanceof PsiField && o2 instanceof PsiField && !o1.equals(o2)) {
      PsiClass clazz = ((PsiField)o1).getContainingClass();
      return clazz != null && clazz == ((PsiField)o2).getContainingClass();
    }
    return false;
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
    if (refElement instanceof PsiNameValuePair &&
        PsiUtil.isAnnotationMethod(target) &&
        PsiTypes.booleanType().equals(((PsiMethod)target).getReturnType())) {
      return false; // they're suggested, but with true/false value text (IDEA-121071)
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
    if (target instanceof PsiField) {
      if (SyntaxTraverser.psiApi().parents(ref).find(e -> e instanceof PsiMethod && ((PsiMethod)e).isConstructor()) != null) {
        // https://youtrack.jetbrains.com/issue/IDEA-174744 on red code
        return false;
      }
      if (!((PsiField)target).hasModifierProperty(PsiModifier.STATIC) && anno != null) {
        return false;
      }
    }
    if (isStaticWithInstanceQualifier(ref, target)) {
      return false;
    }
    if (target instanceof PsiVariable && PsiTreeUtil.isAncestor(target, ref, false)) {
      return false;
    }
    if (anno != null &&
        ref.getParent() instanceof PsiNameValuePair &&
        !JavaCompletionContributor.mayCompleteValueExpression(ref, anno.resolveAnnotationType())) {
      return false;
    }
    return true;
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
    if (leaf.textMatches(JavaKeywords.TRUE) || leaf.textMatches(JavaKeywords.FALSE)) {
      return false; // boolean literal presence depends on expected types, which can be missing in red files
    }
    if (PsiUtil.isSoftKeyword(leaf.getText(), LanguageLevel.JDK_1_9) &&
        !PsiJavaModule.MODULE_INFO_FILE.equals(leaf.getContainingFile().getName())) {
      return false;
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
