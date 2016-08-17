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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class InlineHintsPresentationManager implements Disposable {
  private static final Key<FontMetrics> HINT_FONT_METRICS = Key.create("ParameterHintFontMetrics");
  private static final Key<AnimationStep> ANIMATION_STEP = Key.create("ParameterHintAnimationStep");

  private static final int ANIMATION_STEP_MS = 25;
  private static final int ANIMATION_CHARS_PER_STEP = 3;

  private final Alarm myAlarm = new Alarm(this);

  public static InlineHintsPresentationManager getInstance() {
    return ServiceManager.getService(InlineHintsPresentationManager.class);
  }

  private InlineHintsPresentationManager() {
  }

  public boolean isInlineHint(@NotNull Inlay inlay) {
    return inlay.getRenderer() instanceof MyRenderer;
  }

  public String getHintText(@NotNull Inlay inlay) {
    Inlay.Renderer renderer = inlay.getRenderer();
    return renderer instanceof MyRenderer ? ((MyRenderer)renderer).myText : null;
  }

  public void addHint(@NotNull Editor editor, int offset, @NotNull String hintText, boolean useAnimation) {
    MyRenderer renderer = new MyRenderer(editor, hintText, useAnimation);
    Inlay inlay = editor.getInlayModel().addElement(offset, Inlay.Type.INLINE, renderer);
    if (useAnimation && inlay != null) {
      scheduleRendererUpdate(editor, inlay);
    }
  }

  public void deleteHint(@NotNull Editor editor, @NotNull Inlay hint) {
    updateRenderer(editor, hint, null);
  }

  public void replaceHint(@NotNull Editor editor, @NotNull Inlay hint, @NotNull String newText) {
    updateRenderer(editor, hint, newText);
  }

  private void updateRenderer(@NotNull Editor editor, @NotNull Inlay hint, @Nullable String newText) {
    MyRenderer renderer = (MyRenderer)hint.getRenderer();
    renderer.update(editor, newText);
    hint.update();
    scheduleRendererUpdate(editor, hint);
  }

  @Override
  public void dispose() {}

  private void scheduleRendererUpdate(Editor editor, Inlay inlay) {
    AnimationStep step = editor.getUserData(ANIMATION_STEP);
    if (step == null) {
      editor.putUserData(ANIMATION_STEP, step = new AnimationStep(editor));
    }
    step.inlays.add(inlay);
    scheduleAnimationStep(step);
  }

  private void scheduleAnimationStep(AnimationStep step) {
    myAlarm.cancelRequest(step);
    myAlarm.addRequest(step, ANIMATION_STEP_MS, ModalityState.any());
  }

  private static Font getFont(@NotNull Editor editor) {
    return getFontMetrics(editor).getFont();
  }

  private static FontMetrics getFontMetrics(@NotNull Editor editor) {
    String familyName = UIManager.getFont("Label.font").getFamily();
    int size = Math.max(1, editor.getColorsScheme().getEditorFontSize() - 1);
    FontMetrics metrics = editor.getUserData(HINT_FONT_METRICS);
    if (metrics != null) {
      Font font = metrics.getFont();
      if (!familyName.equals(font.getFamily()) || size != font.getSize()) metrics = null;
    }
    if (metrics == null) {
      Font font = new Font(familyName, Font.PLAIN, size);
      metrics = FontInfo.createReferenceGraphics().getFontMetrics(font);
      editor.putUserData(HINT_FONT_METRICS, metrics);
    }
    return metrics;
  }

  private static class MyRenderer extends Inlay.Renderer {
    private String myText;
    private int startWidth;
    private int endWidth;
    private int steps;
    private int step;

    private MyRenderer(Editor editor, String text, boolean animated) {
      updateState(editor, text, 0);
      if (!animated) step = steps + 1;
    }

    public void update(Editor editor, String newText) {
      updateState(editor, newText, currentWidth());
    }

    private void updateState(Editor editor, String text, int startWidth) {
      myText = text;
      this.startWidth = startWidth;
      FontMetrics metrics = getFontMetrics(editor);
      endWidth = myText == null ? 0 : metrics.stringWidth(myText) + 14;
      step = 1;
      steps = Math.max(1, Math.abs(endWidth - startWidth) / metrics.charWidth(' ') / ANIMATION_CHARS_PER_STEP);
    }

    public boolean nextStep() {
      return ++step <= steps;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      return currentWidth();
    }

    private int currentWidth() {
      return step <= steps ? Math.max(1, startWidth + (endWidth - startWidth) / steps * step) : endWidth;
    }

    @Override
    public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
      if (myText != null && (step > steps || startWidth != 0)) {
        TextAttributes attributes = editor.getColorsScheme().getAttributes(JavaHighlightingColors.INLINE_PARAMETER_HINT);
        if (attributes != null) {
          GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
          Color backgroundColor = attributes.getBackgroundColor();
          g.setColor(ColorUtil.brighter(backgroundColor, 1));
          g.fillRoundRect(r.x + 2, r.y + 1, r.width - 4, r.height - 4, 4, 4);
          g.setColor(ColorUtil.darker(backgroundColor, 1));
          g.fillRoundRect(r.x + 2, r.y + 3, r.width - 4, r.height - 4, 4, 4);
          g.setColor(backgroundColor);
          g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 4, 4);
          g.setColor(attributes.getForegroundColor());
          g.setFont(getFont(editor));
          FontMetrics metrics = g.getFontMetrics();
          Shape savedClip = g.getClip();
          g.clipRect(r.x + 3, r.y + 3, r.width - 6, r.height - 6);
          g.drawString(myText, r.x + 7, r.y + (r.height + metrics.getAscent() - metrics.getDescent()) / 2 - 1);
          g.setClip(savedClip);
          config.restore();
        }
      }
    }
  }

  private class AnimationStep implements Runnable {
    private final Editor myEditor;
    private final Set<Inlay> inlays = new HashSet<>();

    AnimationStep(Editor editor) {
      myEditor = editor;
    }

    @Override
    public void run() {
      Iterator<Inlay> it = inlays.iterator();
      while (it.hasNext()) {
        Inlay inlay = it.next();
        if (inlay.isValid()) {
          MyRenderer renderer = (MyRenderer)inlay.getRenderer();
          if (!renderer.nextStep()) {
            it.remove();
          }
          if (renderer.currentWidth() == 0) {
            Disposer.dispose(inlay);
          }
          else {
            inlay.update();
          }
        }
        else {
          it.remove();
        }
      }
      if (inlays.isEmpty()) {
        myEditor.putUserData(ANIMATION_STEP, null);
      }
      else {
        scheduleAnimationStep(this);
      }
    }
  }
}
