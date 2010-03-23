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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ExpressionPsiElement extends CompositePsiElement {
  private final int myHC = ourHC++;

  @Override
  public int hashCode() {
    return myHC;
  }

  public ExpressionPsiElement(final IElementType type) {
    super(type);
  }

  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      boolean needParenth = ReplaceExpressionUtil.isNeedParenthesis(child, newElement);
      if (needParenth) {
        newElement = SourceUtil.addParenthToReplacedChild(JavaElementType.PARENTH_EXPRESSION, newElement, getManager());
      }
    }
    super.replaceChildInternal(child, newElement);
  }
}
