// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaErrorFormatUtil {
  static @NotNull @NlsSafe String formatMethod(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                      PsiFormatUtilBase.SHOW_TYPE);
  }

  static @NotNull @NlsSafe String formatClass(@NotNull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  static @NotNull String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  static @NotNull String formatField(@NotNull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
  }

  static @NotNull TextRange getMethodDeclarationTextRange(@NotNull PsiMethod method) {
    if (method instanceof SyntheticElement) return TextRange.EMPTY_RANGE;
    int start = stripAnnotationsFromModifierList(method.getModifierList());
    TextRange throwsRange = method.getThrowsList().getTextRange();
    int end = throwsRange.getEndOffset();
    return new TextRange(start, end).shiftLeft(method.getTextRange().getStartOffset());
  }

  static @NotNull TextRange getFieldDeclarationTextRange(@NotNull PsiField field) {
    PsiModifierList modifierList = field.getModifierList();
    TextRange range = field.getTextRange();
    int start = modifierList == null ? range.getStartOffset() : stripAnnotationsFromModifierList(modifierList);
    int end = field.getNameIdentifier().getTextRange().getEndOffset();
    return new TextRange(start, end).shiftLeft(range.getStartOffset());
  }

  static @NotNull TextRange getClassDeclarationTextRange(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      throw new IllegalArgumentException();
    }
    PsiElement psiElement = aClass instanceof PsiAnonymousClass anonymousClass
                            ? anonymousClass.getBaseClassReference()
                            : aClass.getModifierList() == null ? aClass.getNameIdentifier() : aClass.getModifierList();
    if(psiElement == null) return new TextRange(0, 0);
    int start = stripAnnotationsFromModifierList(psiElement);
    PsiElement endElement = aClass instanceof PsiAnonymousClass anonymousClass ?
                            anonymousClass.getBaseClassReference() :
                            aClass.getImplementsList();
    if (endElement == null) endElement = aClass.getNameIdentifier();
    TextRange endTextRange = endElement == null ? null : endElement.getTextRange();
    int end = endTextRange == null ? start : endTextRange.getEndOffset();
    return new TextRange(start, end).shiftLeft(aClass.getTextRange().getStartOffset());
  }

  private static int stripAnnotationsFromModifierList(@NotNull PsiElement element) {
    TextRange textRange = element.getTextRange();
    if (textRange == null) return 0;
    PsiAnnotation lastAnnotation = null;
    for (PsiElement child = element.getLastChild(); child != null; child = child.getPrevSibling()) {
      if (child instanceof PsiAnnotation) {
        lastAnnotation = (PsiAnnotation)child;
        break;
      }
    }
    if (lastAnnotation == null) {
      return textRange.getStartOffset();
    }
    ASTNode node = lastAnnotation.getNode();
    if (node != null) {
      do {
        node = TreeUtil.nextLeaf(node);
      }
      while (node != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(node.getElementType()));
    }
    if (node != null) return node.getTextRange().getStartOffset();
    return textRange.getStartOffset();
  }

  static @NotNull String formatType(@Nullable PsiType type) {
    return type == null ? PsiKeyword.NULL : PsiTypesUtil.removeExternalAnnotations(type).getInternalCanonicalText();
  }

  static @NlsSafe @NotNull String format(@NotNull PsiElement element) {
    if (element instanceof PsiClass psiClass) return formatClass(psiClass);
    if (element instanceof PsiMethod psiMethod) return formatMethod(psiMethod);
    if (element instanceof PsiField psiField) return formatField(psiField);
    if (element instanceof PsiLabeledStatement statement) return statement.getName() + ':';
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }
}
