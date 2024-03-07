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
package com.intellij.lang;


import org.jetbrains.annotations.Nullable;

/**
 * Represents a lighter AST node that is backed by {@link SyntaxTreeBuilder}.
 * Nodes of this type are composite nodes (non-leaf) and can provide efficient access
 * to their underlying text without the need to recursively visit their children.
 */
public interface LighterASTSyntaxTreeBuilderBackedNode extends LighterASTNode {
  /**
   * Returns null when parsing is not done yet. This method should be called after parsing is finished.
   */
  @Nullable CharSequence getText();
}
