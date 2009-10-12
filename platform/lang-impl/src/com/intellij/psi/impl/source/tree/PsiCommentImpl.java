/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.injected.CommentLiteralEscaper;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, PsiLanguageInjectionHost {
  public PsiCommentImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitComment(this);
  }

  public String toString(){
    return "PsiComment(" + getElementType().toString() + ")";
  }

  @Nullable
  public List<Pair<PsiElement, TextRange>> getInjectedPsi() {
    return InjectedLanguageUtil.getInjectedPsiFiles(this);
  }

  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    return (PsiCommentImpl)replaceWithText(text);
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiComment.class);
  }

  @NotNull
  public LiteralTextEscaper<PsiCommentImpl> createLiteralTextEscaper() {
    return new CommentLiteralEscaper(this);
  }

  public void processInjectedPsi(@NotNull InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
  }
}
