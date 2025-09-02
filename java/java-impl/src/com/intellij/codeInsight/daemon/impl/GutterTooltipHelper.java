// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.javadoc.JavaDocHighlightingManagerImpl;
import com.intellij.ide.actions.QualifiedNameProviderUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.documentation.QuickDocHighlightingHelper;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.psi.util.PsiTreeUtil.getStubOrPsiParentOfType;

public final class GutterTooltipHelper extends GutterTooltipBuilder {
  private GutterTooltipHelper() {
  }

  /**
   * @param elements        a collection of elements to create a formatted tooltip text
   * @param prefix          a text to insert before all elements
   * @param skipFirstMember {@code true} to skip a method (or field) name in the link to element
   * @param actionId        an action identifier to generate context help or {@code null} if not applicable
   */
  public static @NotNull <E extends PsiElement> String getTooltipText(@NotNull Collection<E> elements,
                                                             @NotNull String prefix,
                                                             boolean skipFirstMember,
                                                             @Nullable String actionId) {
    return new GutterTooltipHelper().buildTooltipText(elements, prefix, skipFirstMember, actionId);
  }

  /**
   * @param elements        a collection of elements to create a formatted tooltip text
   * @param prefix          a text to insert before all elements
   * @param skipFirstMember {@code true} to skip a method (or field) name in the link to element
   * @param actionId        an action identifier to generate context help or {@code null} if not applicable
   * @param pressMessageKey JavaBundle key to retrieve context help message with shortcut
   */
  public static @NotNull <E extends PsiElement> String getTooltipText(@NotNull Collection<E> elements,
                                                             @NotNull String prefix,
                                                             boolean skipFirstMember,
                                                             @Nullable String actionId,
                                                             @NotNull String pressMessageKey) {
    String firstDivider = getElementDivider(true, true, elements.size());
    String nextDivider = getElementDivider(false, true, elements.size());
    AtomicReference<String> reference = new AtomicReference<>(firstDivider); // optimization: calculate next divider only once
    return new GutterTooltipHelper().buildTooltipText(prefix, elements, e -> reference.getAndSet(nextDivider), e -> skipFirstMember,
                                                      actionId, pressMessageKey);
  }

  /**
   * @param elements                 a collection of elements to create a formatted tooltip text
   * @param elementToPrefix          a function that returns a text to insert before the current element
   * @param skipFirstMemberOfElement a function that returns {@code true} to skip a method (or field) name for the current element
   * @param actionId                 an action identifier to generate context help or {@code null} if not applicable
   */
  public static @NotNull <E extends PsiElement> String getTooltipText(@NotNull Collection<? extends E> elements,
                                                             @NotNull Function<? super E, String> elementToPrefix,
                                                             @NotNull Predicate<? super E> skipFirstMemberOfElement,
                                                             @Nullable String actionId) {
    return new GutterTooltipHelper().buildTooltipText(elements, elementToPrefix, skipFirstMemberOfElement, actionId);
  }


  @Override
  protected @NotNull String getLinkProtocol() {
    return "element";
  }

  @Override
  protected boolean shouldSkipAsFirstElement(@NotNull PsiElement element) {
    return element instanceof PsiMethod || element instanceof PsiField;
  }

  @Override
  protected @Nullable String getLinkReferenceText(@NotNull PsiElement element) {
    PsiClass psiClass = element instanceof PsiClass ? (PsiClass)element : getStubOrPsiParentOfType(element, PsiClass.class);
    if (psiClass instanceof PsiAnonymousClass) return null;
    return QualifiedNameProviderUtil.getQualifiedName(element);
  }

  @Override
  protected @Nullable PsiElement getContainingElement(@NotNull PsiElement element) {
    PsiMember member = getStubOrPsiParentOfType(element, PsiMember.class);
    if (member == null && element instanceof PsiMember) {
      member = ((PsiMember)element).getContainingClass();
    }
    return member != null ? member : element.getContainingFile();
  }

  @Override
  protected @Nullable String getPresentableName(@NotNull PsiElement element) {
    if (element instanceof PsiEnumConstantInitializer initializer) {
      return QuickDocHighlightingHelper.getStyledFragment(
        initializer.getEnumConstant().getName(),
        JavaDocHighlightingManagerImpl.getInstance().getEnumNameAttributes()
      );
    }
    if (element instanceof PsiAnonymousClass) {
      return QuickDocHighlightingHelper.getStyledFragment(
        JavaBundle.message("tooltip.anonymous"),
        JavaDocHighlightingManagerImpl.getInstance().getAnonymousClassNameAttributes());
    }
    if (element instanceof PsiNamedElement named) {
      TextAttributes attributes = null;
      if (element instanceof PsiClass cls) {
        attributes = JavaDocHighlightingManagerImpl.getInstance().getClassDeclarationAttributes(cls);
      }
      else if (element instanceof PsiMethod method) {
        attributes = JavaDocHighlightingManagerImpl.getInstance().getMethodDeclarationAttributes(method);
      }
      else if (element instanceof PsiParameter) {
        attributes = JavaDocHighlightingManagerImpl.getInstance().getParameterAttributes();
      }
      else if (element instanceof PsiField field) {
        attributes = JavaDocHighlightingManagerImpl.getInstance().getFieldDeclarationAttributes(field);
      }
      String name = named.getName();
      if (attributes != null && name != null) {
        return QuickDocHighlightingHelper.getStyledFragment(name, attributes);
      }
      else {
        return name;
      }
    }
    return null;
  }

  @Override
  protected void appendElement(@NotNull StringBuilder sb, @NotNull PsiElement element, boolean skip) {
    super.appendElement(sb, element, skip);
    PsiFile psiFile = element.getContainingFile();
    if (psiFile instanceof PsiClassOwner) {
      appendPackageName(sb, ((PsiClassOwner)psiFile).getPackageName());
    }
  }

  @Override
  protected boolean isDeprecated(@NotNull PsiElement element) {
    return element instanceof PsiDocCommentOwner owner && owner.isDeprecated();
  }
}
