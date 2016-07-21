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
package com.intellij.lang;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abstract syntax tree built up from light nodes.
 */
public abstract class LighterAST {
  private final CharTable myCharTable;

  public LighterAST(@NotNull CharTable charTable) {
    myCharTable = charTable;
  }

  @NotNull
  public CharTable getCharTable() {
    return myCharTable;
  }

  @NotNull
  public abstract LighterASTNode getRoot();

  @Nullable
  public abstract LighterASTNode getParent(@NotNull final LighterASTNode node);

  @NotNull
  public abstract List<LighterASTNode> getChildren(@NotNull final LighterASTNode parent);

  public abstract void disposeChildren(@NotNull List<LighterASTNode> children);

  @Nullable
  public LighterASTNode findChildByType(@Nullable LighterASTNode node, @NotNull final IElementType type) {
    if (node == null) return null;
    return ContainerUtil.find(getChildren(node), new Condition<LighterASTNode>() {
      @Override
      public boolean value(LighterASTNode node) {
        return node.getTokenType() == type;
      }
    });
  }

  @NotNull
  public List<LighterASTNode> findChildrenByType(@NotNull LighterASTNode node, @NotNull final TokenSet types) {
    return ContainerUtil.filter(getChildren(node), new Condition<LighterASTNode>() {
      @Override
      public boolean value(LighterASTNode node) {
        return types.contains(node.getTokenType());
      }
    });
  }
}