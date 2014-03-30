/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

public interface NodeStructure<T> {
  NodeStructure<LighterASTNode> LIGHTER_NODE_STRUCTURE = new NodeStructure<LighterASTNode>() {
    @Override
    public int getStartOffset(LighterASTNode node) {
      return node.getStartOffset();
    }

    @Override
    public int getEndOffset(LighterASTNode node) {
      return node.getEndOffset();
    }

    @Override
    public IElementType getTokenType(LighterASTNode node) {
      return node.getTokenType();
    }
  };


  NodeStructure<ASTNode> AST_NODE_STRUCTURE = new NodeStructure<ASTNode>() {
    @Override
    public int getStartOffset(ASTNode node) {
      return node.getTextRange().getStartOffset();
    }

    @Override
    public int getEndOffset(ASTNode node) {
      return node.getTextRange().getEndOffset();
    }

    @Override
    public IElementType getTokenType(ASTNode node) {
      return node.getElementType();
    }
  };

  int getStartOffset(T node);
  int getEndOffset(T node);
  IElementType getTokenType(T node);
}
