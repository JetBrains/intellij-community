// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.highlighting;

import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

@ApiStatus.Internal
public final class InlineElementData extends HighlightData {
  private final String myText;
  private final boolean myAddBorder;

  public InlineElementData(int offset, TextAttributesKey attributesKey, String text, ColorKey additionalColorKey) {
    this(offset, attributesKey, text, false, additionalColorKey);
  }

  private InlineElementData(int offset, TextAttributesKey attributesKey, String text, boolean highlighted, ColorKey additionalColorKey) {
    super(offset, offset, attributesKey, additionalColorKey);
    myText = text;
    myAddBorder = highlighted;
  }

  public String getText() {
    return myText;
  }

  @Override
  public void addHighlToView(Editor view, EditorColorsScheme scheme, Map<TextAttributesKey, String> displayText) {
    int offset = getStartOffset();
    RendererWrapper renderer = new RendererWrapper(new HintRenderer(myText) {
      @Override
      protected @Nullable TextAttributes getTextAttributes(@NotNull Editor editor) {
        return editor.getColorsScheme().getAttributes(getHighlightKey());
      }
    }, myAddBorder);
    view.getInlayModel().addInlineElement(offset, false, renderer);
  }

  @Override
  public void addToCollection(@NotNull Collection<? super HighlightData> list, boolean highlighted) {
    list.add(new InlineElementData(getStartOffset(), getHighlightKey(), myText, highlighted, getAdditionalColorKey()));
  }
}
