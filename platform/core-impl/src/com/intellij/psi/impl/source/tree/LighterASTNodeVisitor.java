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

import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.LighterLazyParseableNode;
import org.jetbrains.annotations.NotNull;

public abstract class LighterASTNodeVisitor {
  public abstract void visitNode(@NotNull LighterASTNode node);
  public void visitTokenNode(@NotNull LighterASTTokenNode node) {
    visitNode(node);
  }
  public void visitLazyParseableNode(@NotNull LighterLazyParseableNode node) {
    visitNode(node);
  }
}
