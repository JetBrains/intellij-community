/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.application.options.colors.highlighting;

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.editor.colors.CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES;

public class InlineElementData extends HighlightData {
  private final String myText;
  private boolean myAddBorder;

  public InlineElementData(int offset, TextAttributesKey attributesKey, String text) {
    this(offset, attributesKey, text, false);
  }

  private InlineElementData(int offset, TextAttributesKey attributesKey, String text, boolean highlighted) {
    super(offset, offset, attributesKey);
    myText = text;
    myAddBorder = highlighted;
  }

  public String getText() {
    return myText;
  }

  @Override
  public void addHighlToView(Editor view, EditorColorsScheme scheme, Map<TextAttributesKey, String> displayText) {
    int offset = getStartOffset();
    ParameterHintsPresentationManager hintsPresentationManager = ParameterHintsPresentationManager.getInstance();
    Inlay hint = hintsPresentationManager.addHint(view, offset, false, myText, false);
    hintsPresentationManager.setHighlighted(hint, 
                                            DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT_HIGHLIGHTED.equals(getHighlightKey()));
    hintsPresentationManager.setCurrent(hint, myText.contains("current"));
    List<Inlay> inlays = view.getInlayModel().getInlineElementsInRange(offset, offset);
    for (Inlay inlay : inlays) {
      EditorCustomElementRenderer renderer = inlay.getRenderer();
      if (!(renderer instanceof RendererWrapper)) {
        Disposer.dispose(inlay);
        RendererWrapper wrapper = new RendererWrapper(renderer);
        wrapper.drawBorder = myAddBorder;
        view.getInlayModel().addInlineElement(offset, wrapper);
      }
    }
  }

  @Override
  public void addToCollection(@NotNull Collection<HighlightData> list, boolean highlighted) {
    list.add(new InlineElementData(getStartOffset(), getHighlightKey(), myText, highlighted));
  }

  public static class RendererWrapper implements EditorCustomElementRenderer {
    private final EditorCustomElementRenderer myDelegate;
    private boolean drawBorder;

    public RendererWrapper(EditorCustomElementRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      return myDelegate.calcWidthInPixels(editor);
    }

    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      myDelegate.paint(editor, g, r, textAttributes);
      if (drawBorder) {
        TextAttributes attributes = editor.getColorsScheme().getAttributes(BLINKING_HIGHLIGHTS_ATTRIBUTES);
        if (attributes != null && attributes.getEffectColor() != null) {
          g.setColor(attributes.getEffectColor());
          g.drawRect(r.x, r.y, r.width, r.height);
        }
      }
    }
  }

}
