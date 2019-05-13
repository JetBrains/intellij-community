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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;

/**
 * @author gregsh
 */
public class HighlighterIteratorWrapper implements HighlighterIterator {
  private final HighlighterIterator myOriginal;

  public HighlighterIteratorWrapper(HighlighterIterator original) {
    myOriginal = original;
  }

  @Override
  public TextAttributes getTextAttributes() {
    return myOriginal.getTextAttributes();
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
