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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ParameterHintsPresentationManager implements Disposable {
  private static final Key<MyFontMetrics> HINT_FONT_METRICS = Key.create("ParameterHintFontMetrics");
  private static final Key<AnimationStep> ANIMATION_STEP = Key.create("ParameterHintAnimationStep");

  private static final int ANIMATION_STEP_MS = 25;
  private static final int ANIMATION_CHARS_PER_STEP = 3;
  private static final float BACKGROUND_ALPHA = 0.55f;

  private final Alarm myAlarm = new Alarm(this);

  public static ParameterHintsPresentationManager getInstance() {
    return ServiceManager.getService(ParameterHintsPresentationManager.class);
  }

  private ParameterHintsPresentationManager() {
  }

  public boolean isParameterHint(@NotNull Inlay inlay) {
    return inlay.getRenderer() instanceof MyRenderer;
  }

  public String getHintText(@NotNull Inlay inlay) {
    EditorCustomElementRenderer renderer = inlay.getRenderer();
    return renderer instanceof MyRenderer ? ((MyRenderer)renderer).getText() : null;
  }

  public void addHint(@NotNull Editor editor, int offset, @NotNull String hintText, boolean useAnimation) {
    MyRenderer renderer = new MyRenderer(editor, hintText, useAnimation);
    Inlay inlay = editor.getInlayModel().addInlineElement(offset, renderer);
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
    hint.updateSize();
    scheduleRendererUpdate(editor, hint);
  }

  @Override
  public void dispose() {
  }

  private void scheduleRendererUpdate(@NotNull Editor editor, @NotNull Inlay inlay) {
    ApplicationManager.getApplication().assertIsDispatchThread(); // to avoid race conditions in "new AnimationStep"
    AnimationStep step = editor.getUserData(ANIMATION_STEP);
    if (step == null) {
      editor.putUserData(ANIMATION_STEP, step = new AnimationStep(editor));
    }
    step.inlays.add(inlay);
    scheduleAnimationStep(step);
  }

  private void scheduleAnimationStep(@NotNull AnimationStep step) {
    myAlarm.cancelRequest(step);
    myAlarm.addRequest(step, ANIMATION_STEP_MS, ModalityState.any());
  }

  private static Font getFont(@NotNull Editor editor) {
    return getFontMetrics(editor).getFont();
  }

  private static MyFontMetrics getFontMetrics(@NotNull Editor editor) {
    String familyName = UIManager.getFont("Label.font").getFamily();
    int size = Math.max(1, editor.getColorsScheme().getEditorFontSize() - 1);
    MyFontMetrics metrics = editor.getUserData(HINT_FONT_METRICS);
    if (metrics != null) {
      Font font = metrics.getFont();
      if (!familyName.equals(font.getFamily()) || size != font.getSize()) metrics = null;
      else {
        FontRenderContext currentContext = FontInfo.getFontRenderContext(editor.getContentComponent());
        if (currentContext.equals(metrics.metrics.getFontRenderContext())) metrics = null;
      }
    }
    if (metrics == null) {
      Font font = new Font(familyName, Font.PLAIN, size);
      metrics = new MyFontMetrics(editor, font);
      editor.putUserData(HINT_FONT_METRICS, metrics);
    }
    return metrics;
  }

  private static class MyFontMetrics {
    private final FontMetrics metrics;
    private final int lineHeight;

    private MyFontMetrics(Editor editor, Font font) {
      metrics = editor.getContentComponent().getFontMetrics(font);
      // We assume this will be a better approximation to a real line height for a given font
      lineHeight = (int)Math.ceil(font.createGlyphVector(metrics.getFontRenderContext(), "Ap").getVisualBounds().getHeight());
    }

    private Font getFont() {
      return metrics.getFont();
    }
  }

  private static class MyRenderer implements EditorCustomElementRenderer {
    private String myText; // text with colon as a suffix
    private int startWidth;
    private int steps;
    private int step;

    private MyRenderer(Editor editor, String text, boolean animated) {
      updateState(editor, text);
      if (!animated) step = steps + 1;
    }

    private String getText() {
      return myText == null ? null : myText.substring(0, myText.length() - 1);
    }

    public void update(Editor editor, String newText) {
      updateState(editor, newText);
    }

    @Nullable
    @Override
    public String getContextMenuGroupId() {
      return "ParameterNameHints";
    }

    private void updateState(Editor editor, String text) {
      FontMetrics metrics = getFontMetrics(editor).metrics;
      startWidth = doCalcWidth(myText, metrics);
      myText = text == null ? null : (text + ":");
      int endWidth = doCalcWidth(myText, metrics);
      step = 1;
      steps = Math.max(1, Math.abs(endWidth - startWidth) / metrics.charWidth('a') / ANIMATION_CHARS_PER_STEP);
    }
    
    public boolean nextStep() {
      return ++step <= steps;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      FontMetrics metrics = getFontMetrics(editor).metrics;
      int endWidth = doCalcWidth(myText, metrics);
      return step <= steps ? Math.max(1, startWidth + (endWidth - startWidth) / steps * step) : endWidth;
    }

    private static int doCalcWidth(@Nullable String text, @NotNull FontMetrics fontMetrics) {
      return text == null ? 0 : fontMetrics.stringWidth(text) + 14;
    }

    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
      if (myText != null && (step > steps || startWidth != 0)) {
        TextAttributes attributes = editor.getColorsScheme().getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT);
        if (attributes != null) {
          MyFontMetrics fontMetrics = getFontMetrics(editor);
          Color backgroundColor = attributes.getBackgroundColor();
          if (backgroundColor != null) {
            GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
            GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA);
            g.setColor(backgroundColor);
            int gap = r.height < (fontMetrics.lineHeight + 2) ? 1 : 2;
            g.fillRoundRect(r.x + 2, r.y + gap, r.width - 4, r.height - gap * 2, 8, 8);
            config.restore();
          }
          Color foregroundColor = attributes.getForegroundColor();
          if (foregroundColor != null) {
            g.setColor(foregroundColor);
            g.setFont(getFont(editor));
            Shape savedClip = g.getClip();
            g.clipRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4);
            int editorAscent = editor instanceof EditorImpl ? ((EditorImpl)editor).getAscent() : 0;
            FontMetrics metrics = fontMetrics.metrics;
            g.drawString(myText, r.x + 7, r.y + Math.max(editorAscent, (r.height + metrics.getAscent() - metrics.getDescent()) / 2) - 1);
            g.setClip(savedClip);
          }
        }
      }
    }
  }

  private class AnimationStep implements Runnable {
    private final Editor myEditor;
    private final Set<Inlay> inlays = new HashSet<>();

    AnimationStep(@NotNull Editor editor) {
      myEditor = editor;
      Disposer.register(((EditorImpl)editor).getDisposable(), () -> myAlarm.cancelRequest(this));
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
          if (renderer.calcWidthInPixels(myEditor) == 0) {
            Disposer.dispose(inlay);
          }
          else {
            inlay.updateSize();
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
