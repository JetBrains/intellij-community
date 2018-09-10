/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiCoreCommentImpl extends LeafPsiElement implements PsiComment {
  public PsiCoreCommentImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitComment(this);
  }

  public String toString(){
    return "PsiComment(" + getElementType().toString() + ")";
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
