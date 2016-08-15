/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InlineHintsPresentationManager implements Disposable, UISettingsListener {
  private static final int ANIMATION_STEP_MS = 25;
  private static final int ANIMATION_CHARS_PER_STEP = 3;

  private final Alarm OUR_ALARM = new Alarm(this);

  private FontInfo myFontInfo;

  public static InlineHintsPresentationManager getInstance() {
    return ServiceManager.getService(InlineHintsPresentationManager.class);
  }

  private InlineHintsPresentationManager() {
    UISettings settings = UISettings.getInstance();
    assert settings != null;
    settings.addUISettingsListener(this, this);
    updateFontInfo();
  }

  private void updateFontInfo() {
    Font font = UIManager.getFont("Label.font");
    font = font.deriveFont(Math.max(1, font.getSize2D() - 1));
    myFontInfo = new FontInfo(font);
  }

  public boolean isInlineHint(@NotNull Inlay inlay) {
    return inlay.getRenderer() instanceof MyRenderer;
  }

  public String getHintText(@NotNull Inlay inlay) {
    Inlay.Renderer renderer = inlay.getRenderer();
    return renderer instanceof MyRenderer ? ((MyRenderer)renderer).myText : null;
  }

  public void addHint(@NotNull Editor editor, int offset, @NotNull String hintText, boolean useAnimation, int animationStartWidthInPixels) {
    MyRenderer renderer = new MyRenderer(hintText);
    if (useAnimation) {
      AnimationStepRenderer stepRenderer = new AnimationStepRenderer(editor, renderer, animationStartWidthInPixels);
      Inlay inlay = editor.getInlayModel().addElement(offset, Inlay.Type.INLINE, stepRenderer);
      if (inlay != null) {
        OUR_ALARM.addRequest(new AnimationStep(editor, inlay, stepRenderer), ANIMATION_STEP_MS, ModalityState.any());
      }
    }
    else {
      editor.getInlayModel().addElement(offset, Inlay.Type.INLINE, renderer);
    }
  }

  @Override
  public void dispose() {}

  @Override
  public void uiSettingsChanged(UISettings source) {
    updateFontInfo();
  }

  private class MyRenderer extends Inlay.Renderer {
    private final String myText;

    private MyRenderer(String text) {
      myText = text;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      return myFontInfo.fontMetrics().stringWidth(myText) + 14;
    }

    @Override
    public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
      TextAttributes attributes = editor.getColorsScheme().getAttributes(JavaHighlightingColors.INLINE_PARAMETER_HINT);
      if (attributes != null) {
        GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
        Color backgroundColor = attributes.getBackgroundColor();
        g.setColor(ColorUtil.brighter(backgroundColor, 1));
        g.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 4, 4, 4);
        g.setColor(ColorUtil.darker(backgroundColor, 1));
        g.fillRoundRect(r.x + 1, r.y + 3, r.width - 2, r.height - 4, 4, 4);
        g.setColor(backgroundColor);
        g.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height - 4, 4, 4);
        g.setColor(attributes.getForegroundColor());
        g.setFont(myFontInfo.getFont());
        FontMetrics metrics = g.getFontMetrics();
        Shape savedClip = g.getClip();
        g.clipRect(r.x + 2, r.y + 3, r.width - 4, r.height - 6); // support drawing in smaller rectangle (used in animation)
        g.drawString(myText, r.x + 7, r.y + (r.height + metrics.getAscent() - metrics.getDescent()) / 2 - 1);
        g.setClip(savedClip);
        config.restore();
      }
    }
  }

  private class AnimationStepRenderer extends Inlay.Renderer {
    private final Inlay.Renderer renderer;
    private final int startWidth;
    private final int endWidth;
    private final int step;
    private final int steps;

    private AnimationStepRenderer(Editor editor, Inlay.Renderer renderer, int startWidth) {
      this.renderer = renderer;
      this.startWidth = startWidth;
      this.endWidth = renderer.calcWidthInPixels(editor);
      this.step = 1;
      this.steps = Math.max(1, Math.abs(endWidth - startWidth) / myFontInfo.charWidth(' ') / ANIMATION_CHARS_PER_STEP);
    }

    private AnimationStepRenderer(Inlay.Renderer renderer, int startWidth, int endWidth, int step, int steps) {
      this.renderer = renderer;
      this.startWidth = startWidth;
      this.endWidth = endWidth;
      this.step = step;
      this.steps = steps;
    }

    private Inlay.Renderer nextStep() {
      return step < steps ? new AnimationStepRenderer(renderer, startWidth, endWidth, step + 1, steps) : renderer;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      return Math.max(1, startWidth + (endWidth - startWidth) / steps * step);
    }

    @Override
    public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
      if (startWidth > 0) {
        renderer.paint(g, r, editor);
      }
    }
  }

  private class AnimationStep implements Runnable {
    private final Editor myEditor;
    private final Inlay myInlay;
    private final AnimationStepRenderer myRenderer;

    public AnimationStep(Editor editor, Inlay inlay, AnimationStepRenderer renderer) {
      myEditor = editor;
      myInlay = inlay;
      myRenderer = renderer;
    }

    @Override
    public void run() {
      if (!myInlay.isValid()) return;
      int offset = myInlay.getOffset();
      Inlay.Renderer nextRenderer = myRenderer.nextStep();
      Disposer.dispose(myInlay);
      Inlay newInlay = myEditor.getInlayModel().addElement(offset, Inlay.Type.INLINE, nextRenderer);
      if (nextRenderer instanceof AnimationStepRenderer) {
        OUR_ALARM.addRequest(new AnimationStep(myEditor, newInlay, (AnimationStepRenderer)nextRenderer),
                             ANIMATION_STEP_MS, ModalityState.any());
      }
    }
  }
}
