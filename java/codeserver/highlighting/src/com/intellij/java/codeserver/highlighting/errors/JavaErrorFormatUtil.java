// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

final class JavaErrorFormatUtil {
  static @NotNull @NlsContexts.DetailedDescription String formatClashMethodMessage(@NotNull PsiMethod method1, @NotNull PsiMethod method2) {
    PsiClass class1 = method1.getContainingClass();
    PsiClass class2 = method2.getContainingClass();
    if (class1 != null && class2 != null && !class1.isEquivalentTo(class2)) {
      return JavaCompilationErrorBundle.message("clash.methods.message.show.classes",
                                                formatMethod(method1), formatMethod(method2),
                                                formatClass(class1), formatClass(class2));
    }
    return JavaCompilationErrorBundle.message("clash.methods.message", formatMethod(method1), formatMethod(method2));
  }

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

  static @NotNull String formatResolvedSymbol(@NotNull JavaResolveResult result) {
    PsiElement element = result.getElement();
    String symbolName = element == null ? null : HighlightMessageUtil.getSymbolName(element, result.getSubstitutor());
    return symbolName == null ? "?" : symbolName;
  }

  private static PsiElement getContainer(@NotNull PsiElement refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensionList()) {
      PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  static @NotNull String formatResolvedSymbolContainer(@NotNull JavaResolveResult result) {
    PsiElement element = result.getElement();
    PsiElement container = element == null ? null : getContainer(element);
    String symbolName = container == null ? null : HighlightMessageUtil.getSymbolName(container, result.getSubstitutor());
    return symbolName == null ? "?" : symbolName;
  }
  
  static @NotNull String formatArgumentTypes(@Nullable PsiExpressionList list, boolean shortNames) {
    if (list == null) return "";
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    PsiExpression[] args = list.getExpressions();
    for (int i = 0; i < args.length; i++) {
      if (i > 0) builder.append(", ");
      PsiType argType = args[i].getType();
      builder.append(argType != null ? (shortNames ? argType.getPresentableText() : formatType(argType)) : "?");
    }
    builder.append(")");
    return builder.toString();
  }

  static @NotNull @Nls String getRecordMethodKind(@NotNull PsiMethod method) {
    if (JavaPsiRecordUtil.isCompactConstructor(method)) {
      return JavaCompilationErrorBundle.message("record.compact.constructor");
    }
    if (JavaPsiRecordUtil.isCanonicalConstructor(method)) {
      return JavaCompilationErrorBundle.message("record.canonical.constructor");
    }
    if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
      return JavaCompilationErrorBundle.message("record.accessor");
    }
    throw new IllegalArgumentException("Record special method expected: " + method);
  }
  
  static @Nullable TextRange getRange(@NotNull PsiElement element) {
    if (element instanceof PsiMember member) {
      return getMemberDeclarationTextRange(member);
    }
    if (element instanceof PsiJavaModule module) {
      return getModuleRange(module);
    }
    if (element instanceof PsiNewExpression newExpression) {
      PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
      if (reference != null) {
        return reference.getTextRangeInParent();
      }
    }
    if (element instanceof PsiMethodCallExpression callExpression) {
      PsiElement nameElement = callExpression.getMethodExpression().getReferenceNameElement();
      if (nameElement != null) {
        return nameElement.getTextRangeInParent();
      }
    }
    if (element instanceof PsiJavaCodeReferenceElement ref) {
      PsiElement nameElement = ref.getReferenceNameElement();
      if (nameElement != null) {
        return nameElement.getTextRangeInParent();
      }
    }
    PsiElement nextSibling = element.getNextSibling();
    if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.SEMICOLON)) {
      return TextRange.create(0, element.getTextLength() + 1);
    }
    return TextRange.create(0, element.getTextLength());
  }

  static @NotNull TextRange getMethodDeclarationTextRange(@NotNull PsiMethod method) {
    if (method instanceof SyntheticElement) return TextRange.EMPTY_RANGE;
    int start = stripAnnotationsFromModifierList(method.getModifierList());
    int end;
    if (method.getBody() == null) {
      end = method.getTextRange().getEndOffset();
    } else {
      end = method.getThrowsList().getTextRange().getEndOffset();
    }
    return new TextRange(start, end).shiftLeft(method.getTextRange().getStartOffset());
  }

  private static @NotNull TextRange getModuleRange(@NotNull PsiJavaModule module) {
    PsiKeyword kw = PsiTreeUtil.getChildOfType(module, PsiKeyword.class);
    return new TextRange(kw != null ? kw.getTextRangeInParent().getStartOffset() : 0, 
                         module.getNameIdentifier().getTextRangeInParent().getEndOffset());
  }

  static @Nullable TextRange getMemberDeclarationTextRange(@NotNull PsiMember member) {
    return member instanceof PsiClass psiClass ? getClassDeclarationTextRange(psiClass) :
           member instanceof PsiMethod psiMethod ? getMethodDeclarationTextRange(psiMethod) :
           member instanceof PsiField psiField ? getFieldDeclarationTextRange(psiField) :
           null;
  }

  static @NotNull TextRange getFieldDeclarationTextRange(@NotNull PsiField field) {
    PsiModifierList modifierList = field.getModifierList();
    TextRange range = field.getTextRange();
    int start = modifierList == null || modifierList.getParent() != field ? 
                range.getStartOffset() : stripAnnotationsFromModifierList(modifierList);
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

  static @NotNull String formatTypes(@NotNull Collection<? extends PsiClassType> unhandled) {
    return StringUtil.join(unhandled, JavaErrorFormatUtil::formatType, ", ");
  }

  static @NotNull String formatType(@Nullable PsiType type) {
    return type == null ? JavaKeywords.NULL : PsiTypesUtil.removeExternalAnnotations(type).getInternalCanonicalText();
  }

  static @NlsSafe @NotNull String format(@NotNull PsiElement element) {
    if (element instanceof PsiClass psiClass) return formatClass(psiClass);
    if (element instanceof PsiMethod psiMethod) return formatMethod(psiMethod);
    if (element instanceof PsiField psiField) return formatField(psiField);
    if (element instanceof PsiLabeledStatement statement) return statement.getName() + ':';
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  static @NotNull String formatClassOrType(@NotNull PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return psiClass == null ? type.getPresentableText() : formatClass(psiClass);
  }
}
