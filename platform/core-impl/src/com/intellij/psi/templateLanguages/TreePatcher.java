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
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TreePatcher {

  /**
   * Inserts toInsert into destinationTree according to parser rules.
   * <br>
   * Inserting must not change the position (offset) of the new node in the three (otherwise we will receive broken tree)
   */
  void insert(@NotNull CompositeElement parent, @Nullable TreeElement anchorBefore, @NotNull OuterLanguageElement toInsert);

  /**
   * If leaf need to be split to insert OuterLanguageElement this function is called
   *
   * @return first part of the split
   */
  @NotNull
  default LeafElement split(@NotNull LeafElement leaf, int offset, @NotNull CharTable table) {
    CharSequence chars = leaf.getChars();
    LeafElement leftPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, 0, offset));
    LeafElement rightPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, offset, chars.length()));
    leaf.rawInsertAfterMe(leftPart);
    leftPart.rawInsertAfterMe(rightPart);
    leaf.rawRemove();
    return leftPart;
  }

  @NotNull
  default LeafElement removeRange(@NotNull LeafElement leaf,
                                  @NotNull TextRange rangeToRemove,
                                  @NotNull CharTable table) {
    CharSequence chars = leaf.getChars();
    int startOffset = rangeToRemove.getStartOffset();
    CharSequence prefix = startOffset == 0 ? "" : chars.subSequence(0, startOffset);
    CharSequence suffix = chars.subSequence(rangeToRemove.getEndOffset(), chars.length());
    String res = prefix + suffix.toString();
    LeafElement newLeaf = ASTFactory.leaf(leaf.getElementType(), table.intern(res));
    leaf.rawInsertBeforeMe(newLeaf);
    leaf.rawRemove();
    return newLeaf;
  }
}
