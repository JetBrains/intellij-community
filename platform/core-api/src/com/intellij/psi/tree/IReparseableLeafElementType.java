/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

/**
 * An interface to be implemented by {@link IElementType} instances for leaf AST nodes, providing a possibility for quick reparse.
 * @see #reparseLeaf(ASTNode, CharSequence)
 * @since 2016.2
 * @author peter
 */
public interface IReparseableLeafElementType<T extends ASTNode> {

  /**
   * If the IDE detects that the whole changed range to be reparsed is contained within one leaf that has such element type,
   * this method is called. It should analyze the provided new leaf text, check whether this text conforms with the language rules,
   * and (if yes) return a new leaf of the same type with the new text. The reparse process would then just replace the old leaf with
   * the new one. Otherwise, null should be returned, and a full-blown reparse process will happen instead.
   * @param leaf the leaf element where the text change has occurred
   * @param newText the updated text inside the leaf's range
   * @return a replacement leaf with newText text, if it's correct to do such replacement, or null otherwise
   */
  @Nullable
  T reparseLeaf(@NotNull T leaf, @NotNull CharSequence newText);

}
