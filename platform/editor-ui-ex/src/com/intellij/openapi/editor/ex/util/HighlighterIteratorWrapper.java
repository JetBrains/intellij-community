// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public class HighlighterIteratorWrapper implements HighlighterIterator {
  private final @NotNull HighlighterIterator myOriginal;

  public HighlighterIteratorWrapper(@NotNull HighlighterIterator original) {
    myOriginal = original;
  }

  @Override
  public TextAttributes getTextAttributes() {
    return myOriginal.getTextAttributes();
  }

  @Override
  public TextAttributesKey @NotNull [] getTextAttributesKeys() {
    return myOriginal.getTextAttributesKeys();
  }

  @Override
  public int getStart() {
    return myOriginal.getStart();
  }

  @Override
  public int getEnd() {
    return myOriginal.getEnd();
  }

  @Override
  public IElementType getTokenType() {
    return myOriginal.getTokenType();
  }

  @Override
  public void advance() {
    myOriginal.advance();
  }

  @Override
  public void retreat() {
    myOriginal.retreat();
  }

  @Override
  public boolean atEnd() {
    return myOriginal.atEnd();
  }

  @Override
  public Document getDocument() {
    return myOriginal.getDocument();
  }
}
