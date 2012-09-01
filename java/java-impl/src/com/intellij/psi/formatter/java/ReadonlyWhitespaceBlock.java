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

package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ReadonlyWhitespaceBlock implements Block {
  private final TextRange myRange;
  private final Wrap myWrap;
  private final Alignment myAlignment;
  private final Indent myIndent;

  public ReadonlyWhitespaceBlock(final TextRange range, final Wrap wrap, final Alignment alignment, final Indent indent) {
    myRange = range;
    myWrap = wrap;
    myAlignment = alignment;
    myIndent = indent;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myRange;
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  @Nullable
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return ChildAttributes.DELEGATE_TO_NEXT_CHILD;
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }
}
