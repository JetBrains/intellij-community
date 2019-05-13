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
 * An additional interface to be implemented by {@link IElementType} instances that allows to customize AST node creation. Useful for cases when one needs to override
 * various {@link ASTNode} methods. If not implemented AST nodes of type CompositeElement are created.
 *
 * @see ILeafElementType
 * @author peter
 */
public interface ICompositeElementType {

  @NotNull
  ASTNode createCompositeNode();
  
}
