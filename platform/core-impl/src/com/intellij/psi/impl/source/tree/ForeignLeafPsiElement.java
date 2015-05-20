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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ForeignLeafType;
import com.intellij.lang.TokenWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ForeignLeafPsiElement extends LeafPsiElement {
  @NotNull private final ForeignLeafType myForeignType;

  public ForeignLeafPsiElement(@NotNull ForeignLeafType type, CharSequence text) {
    super(dereferenceElementType(type.getDelegate()), text);
    myForeignType = type;
  }

  @NotNull
  private static IElementType dereferenceElementType(@NotNull IElementType type) {
    while ( type instanceof TokenWrapper)
      type = (( TokenWrapper ) type ).getDelegate();

    return type;
  }

  @Override
  public LeafElement findLeafElementAt(int offset) {
    return null;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence seq) {
    return false;
  }

  @Override
  protected int textMatches(@NotNull CharSequence buffer, int start) {
    return start;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  public int getNotCachedLength() {
    return 0;
  }

  @Override
  public int getStartOffset() {
    return 0;
  }

  @NotNull
  public ForeignLeafType getForeignType() {
    return myForeignType;
  }

  @Override
  public String toString() {
    return "ForeignLeaf(" + getElementType() + ": " + getText() + ")";
  }
}
