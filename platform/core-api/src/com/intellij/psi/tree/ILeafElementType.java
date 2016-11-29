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
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * An additional interface to be implemented by {@link IElementType} instances to allow customizing how leaf AST elements are created
 * for tokens of this type. By default, plain LeafElement instances would be created. Implementing this interface only makes sense
 * if you want to override some methods in LeafElement.
 *
 * @see ICompositeElementType
 * @author peter
 */
public interface ILeafElementType {

  /**
   * Invoked by {@link com.intellij.lang.PsiBuilder} to create a leaf AST node based of this type.
   * @return a LeafElement object with the given text
   */
  @NotNull
  ASTNode createLeafNode(@NotNull CharSequence leafText);

}
