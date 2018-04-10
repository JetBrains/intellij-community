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

import com.intellij.lang.ASTFactory;
import com.intellij.lang.DefaultASTFactory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.javadoc.CorePsiDocTagValueImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiKeywordImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CoreJavaASTFactory extends ASTFactory {
  private final DefaultASTFactory myDefaultASTFactory = ServiceManager.getService(DefaultASTFactory.class);

  @Override
  public LeafElement createLeaf(@NotNull final IElementType type, @NotNull final CharSequence text) {
    if (type == JavaTokenType.C_STYLE_COMMENT || type == JavaTokenType.END_OF_LINE_COMMENT) {
      return myDefaultASTFactory.createComment(type, text);
    }
    if (type == JavaTokenType.IDENTIFIER) {
      return new PsiIdentifierImpl(text);
    }
    if (ElementType.KEYWORD_BIT_SET.contains(type)) {
      return new PsiKeywordImpl(type, text);
    }
    if (type instanceof IJavaElementType) {
      return new PsiJavaTokenImpl(type, text);
    }
    if (type instanceof IJavaDocElementType) {
      assert type != JavaDocElementType.DOC_TAG_VALUE_ELEMENT;
      return new PsiDocTokenImpl(type, text);
    }

    return null;
  }

  @Override
  public CompositeElement createComposite(@NotNull IElementType type) {
    if (type == JavaDocElementType.DOC_TAG_VALUE_ELEMENT) {
      return new CorePsiDocTagValueImpl();
    }
    return null;
  }
}
