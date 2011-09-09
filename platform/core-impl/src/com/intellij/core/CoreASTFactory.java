/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.DefaultASTFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CoreASTFactory extends ASTFactory implements DefaultASTFactory {
  @Override
  @NotNull
  public LazyParseableElement createLazy(final ILazyParseableElementType type, final CharSequence text) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, text);
    }

    return new LazyParseableElement(type, text);
  }

  @Override
  @NotNull
  public CompositeElement createComposite(final IElementType type) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, null);
    }

    return new CompositeElement(type);
  }

  @Override
  @NotNull
  public LeafElement createLeaf(IElementType type, CharSequence text) {
    return new LeafPsiElement(type, text);
  }
}
