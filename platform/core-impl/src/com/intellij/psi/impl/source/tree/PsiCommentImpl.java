/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.injected.CommentLiteralEscaper;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, PsiLanguageInjectionHost {
  public PsiCommentImpl(@NotNull IElementType type, @NotNull CharSequence text) {
    super(type, text);
  }

  @NotNull
  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitComment(this);
  }

  @Override
  public String toString(){
    return "PsiComment(" + getElementType().toString() + ")";
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return (PsiCommentImpl)replaceWithText(text);
  }

  @Override
  @NotNull
  public LiteralTextEscaper<PsiCommentImpl> createLiteralTextEscaper() {
    return new CommentLiteralEscaper(this);
  }
}
