// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance(EmptyEditorHighlighter.class);

  private TextAttributes myCachedAttributes;
  private final TextAttributesKey myKey;
  private int myTextLength = 0;
  private HighlighterClient myEditor;

  public EmptyEditorHighlighter() {
    this(null, HighlighterColors.TEXT);
  }

  public EmptyEditorHighlighter(@Nullable EditorColorsScheme scheme, @NotNull TextAttributesKey key) {
    myKey = key;
    myCachedAttributes = scheme != null ? scheme.getAttributes(key) : null;
  }

  public EmptyEditorHighlighter(@Nullable TextAttributes attributes) {
    myCachedAttributes = attributes;
    myKey = HighlighterColors.TEXT;
  }

  /**
   * @deprecated Avoid specifying text attributes. Use {@link TextAttributesKey} instead
   */
  @Deprecated(forRemoval = true)
  public void setAttributes(TextAttributes attributes) {
    myCachedAttributes = attributes;
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    myTextLength = text.length();
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {
    setAttributes(scheme.getAttributes(myKey));
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    myTextLength += e.getNewLength() - e.getOldLength();
  }

  @Override
  public int getPriority() {
    return 2;
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(int startOffset) {
    return new HighlighterIterator(){
      private final TextAttributesKey[] myKeys = new TextAttributesKey[]{myKey};
      private int index = 0;

      @Override
      public TextAttributes getTextAttributes() {
        return myCachedAttributes;
      }

      @Override
      public TextAttributesKey @NotNull [] getTextAttributesKeys() {
        return myKeys;
      }

      @Override
      public int getStart() {
        return 0;
      }

      @Override
      public int getEnd() {
        return myTextLength;
      }

      @Override
      public void advance() {
        index++;
      }

      @Override
      public void retreat(){
        index--;
      }

      @Override
      public boolean atEnd() {
        return index != 0;
      }

      @NotNull
      @Override
      public Document getDocument() {
        return myEditor.getDocument();
      }

      @Override
      public IElementType getTokenType(){
        return IElementType.find(IElementType.FIRST_TOKEN_INDEX);
      }
    };
  }
}