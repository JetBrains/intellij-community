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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecordHeaderElement extends CompositeElement implements Constants {
  private final TokenSet RECORD_TOKEN_SET = TokenSet.create(JavaElementType.RECORD_COMPONENT);

  public RecordHeaderElement() {
    super(RECORD_HEADER);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.RECORD_COMPONENT) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByType(JavaTokenType.RPARENTH);
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByType(JavaTokenType.LPARENTH);
        before = Boolean.FALSE;
      }
    }

    TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == JavaElementType.RECORD_COMPONENT) {
      JavaSourceUtil.addSeparatingComma(this, first, RECORD_TOKEN_SET);
    }
    return firstAdded;
  }
}
