// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.lang.documentation.InlineDocumentation;
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PsiCommentInlineDocumentation implements InlineDocumentation {

  private final @NotNull PsiDocCommentBase myComment; // might be fake

  PsiCommentInlineDocumentation(@NotNull PsiDocCommentBase comment) {
    myComment = comment;
  }

  @NotNull PsiDocCommentBase getComment() {
    return myComment;
  }

  @NotNull PsiElement getContext() {
    return ObjectUtils.notNull(myComment.getOwner(), myComment);
  }

  @Override
  public @NotNull TextRange getDocumentationRange() {
    return myComment.getTextRange(); // fake comments are still expected to return text range
  }

  @Override
  public @Nullable TextRange getDocumentationOwnerRange() {
    PsiElement owner = myComment.getOwner();
    return owner == null ? null : owner.getTextRange();
  }

  @Override
  public @Nls @Nullable String renderText() {
    return DocumentationManager.getProviderFromElement(myComment).generateRenderedDoc(myComment);
  }

  @SuppressWarnings("TestOnlyProblems") // KTIJ-19938
  @Override
  public DocumentationTarget getOwnerTarget() {
    PsiElement context = getContext();
    return new PsiElementDocumentationTarget(context.getProject(), context);
  }
}
