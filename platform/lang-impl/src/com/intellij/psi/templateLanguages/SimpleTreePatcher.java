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
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;

public class SimpleTreePatcher implements TreePatcher {
  @Override
  public void insert(CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert) {
    if(anchorBefore != null) {
      anchorBefore.rawInsertBeforeMe((TreeElement)toInsert);
    }
    else parent.rawAddChildren((TreeElement)toInsert);
  }

  @Override
  public LeafElement split(LeafElement leaf, int offset, final CharTable table) {
    final CharSequence chars = leaf.getChars();
    final LeafElement leftPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, 0, offset));
    final LeafElement rightPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, offset, chars.length()));
    leaf.rawInsertAfterMe(leftPart);
    leftPart.rawInsertAfterMe(rightPart);
    leaf.rawRemove();
    return leftPart;
  }
}
