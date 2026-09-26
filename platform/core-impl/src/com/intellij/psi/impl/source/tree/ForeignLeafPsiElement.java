// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ForeignLeafType;
import com.intellij.lang.TokenWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * A leaf PSI element representing content that is <em>not</em> literally present in the original source text
 * (e.g., tokens produced by C/C++ macro expansion).
 *
 * <p>Unlike a regular {@link LeafPsiElement}, a foreign leaf is structurally part of the PSI tree but
 * <b>invisible to text-based operations</b>: it reports zero {@linkplain #getTextLength() text length},
 * zero {@linkplain #getStartOffset() start offset}, and never {@linkplain #textMatches matches} any text.
 * {@link #findLeafElementAt(int)} returns {@code null}, so offset-based lookups skip over it.</p>
 *
 * <p>The element wraps a {@link ForeignLeafType} (a {@link TokenWrapper} subclass)
 * that carries the substituted text and the original delegate {@link IElementType}.
 * The constructor dereferences through any {@code TokenWrapper} chain to find the base element type
 * for the superclass, while keeping the {@code ForeignLeafType} accessible via {@link #getForeignType()}.</p>
 *
 * @author max
 * @see ForeignLeafType
 * @see TokenWrapper
 */
public class ForeignLeafPsiElement extends LeafPsiElement {
  private final @NotNull ForeignLeafType myForeignType;

  public ForeignLeafPsiElement(@NotNull ForeignLeafType type, CharSequence text) {
    super(dereferenceElementType(type.getDelegate()), text);
    myForeignType = type;
  }

  private static @NotNull IElementType dereferenceElementType(@NotNull IElementType type) {
    while (type instanceof TokenWrapper) {
      type = ((TokenWrapper)type).getDelegate();
    }

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
  public int getStartOffset() {
    return 0;
  }

  @Override
  public TextRange getTextRange() {
    return new TextRange(0, 0);
  }

  public @NotNull ForeignLeafType getForeignType() {
    return myForeignType;
  }

  @Override
  public String toString() {
    return "ForeignLeaf(" + getElementType() + ": " + getText() + ")";
  }
}
