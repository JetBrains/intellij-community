/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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

  public Inlay addHint(@NotNull Editor editor, int offset, boolean relatesToPrecedingText, @NotNull String hintText, boolean useAnimation) {
    MyRenderer renderer = new MyRenderer(editor, hintText, useAnimation);
    Inlay inlay = editor.getInlayModel().addInlineElement(offset, relatesToPrecedingText, renderer);
    if (inlay != null) {
      if (useAnimation) scheduleRendererUpdate(editor, inlay);
    }
    return inlay;
  }

  public void deleteHint(@NotNull Editor editor, @NotNull Inlay hint, boolean useAnimation) {
    if (useAnimation) {
      updateRenderer(editor, hint, null);
    }
    else {
      Disposer.dispose(hint);  
    }
  }

  public void replaceHint(@NotNull Editor editor, @NotNull Inlay hint, @NotNull String newText) {
    updateRenderer(editor, hint, newText);
  }

  public void setHighlighted(@NotNull Inlay hint, boolean highlighted) {
    if (!isParameterHint(hint)) throw new IllegalArgumentException("Not a parameter hint");
    MyRenderer renderer = (MyRenderer)hint.getRenderer();
    boolean oldValue = renderer.highlighted;
    if (highlighted != oldValue) {
      renderer.highlighted = highlighted;
      hint.repaint();
    }
  }

  public boolean isHighlighted(@NotNull Inlay hint) {
    if (!isParameterHint(hint)) throw new IllegalArgumentException("Not a parameter hint");
    MyRenderer renderer = (MyRenderer)hint.getRenderer();
    return renderer.highlighted;
  }

  public void setCurrent(@NotNull Inlay hint, boolean current) {
    if (!isParameterHint(hint)) throw new IllegalArgumentException("Not a parameter hint");
    MyRenderer renderer = (MyRenderer)hint.getRenderer();
    boolean oldValue = renderer.current;
    if (current != oldValue) {
      renderer.current = current;
      hint.repaint();
    }
  }

  public boolean isCurrent(@NotNull Inlay hint) {
    if (!isParameterHint(hint)) throw new IllegalArgumentException("Not a parameter hint");
    MyRenderer renderer = (MyRenderer)hint.getRenderer();
    return renderer.current;
  }

  private void updateRenderer(@NotNull Editor editor, @NotNull Inlay hint, @Nullable String newText) {
    MyRenderer renderer = (MyRenderer)hint.getRenderer();
    renderer.update(editor, newText, true);
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

  @TestOnly
  public boolean isAnimationInProgress(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return editor.getUserData(ANIMATION_STEP) != null;
  }

  private static Font getFont(@NotNull Editor editor) {
    return getFontMetrics(editor).getFont();
  }

  private static MyFontMetrics getFontMetrics(@NotNull Editor editor) {
    String familyName = UIManager.getFont("Label.font").getFamily();
    int size = Math.max(1, editor.getColorsScheme().getEditorFontSize() - 1);
    MyFontMetrics metrics = editor.getUserData(HINT_FONT_METRICS);
    if (metrics != null && !metrics.isActual(editor, familyName, size)) {
      metrics = null;
    }
    if (metrics == null) {
      metrics = new MyFontMetrics(editor, familyName, size);
      editor.putUserData(HINT_FONT_METRICS, metrics);
    }
    return metrics;
  }

  private static class MyFontMetrics {
    private final FontMetrics metrics;
    private final int lineHeight;

    private MyFontMetrics(Editor editor, String familyName, int size) {
      Font font = new Font(familyName, Font.PLAIN, size);
      FontRenderContext context = getCurrentContext(editor);
      metrics = FontInfo.getFontMetrics(font, context);
      // We assume this will be a better approximation to a real line height for a given font
      lineHeight = (int)Math.ceil(font.createGlyphVector(context, "Ap").getVisualBounds().getHeight());
    }

    private boolean isActual(Editor editor, String familyName, int size) {
      Font font = metrics.getFont();
      if (!familyName.equals(font.getFamily()) || size != font.getSize()) return false;
      FontRenderContext currentContext = getCurrentContext(editor);
      return currentContext.equals(metrics.getFontRenderContext());
    }

    private static FontRenderContext getCurrentContext(@NotNull Editor editor) {
      FontRenderContext editorContext = FontInfo.getFontRenderContext(editor.getContentComponent());
      return new FontRenderContext(editorContext.getTransform(), 
                                   AntialiasingType.getKeyForCurrentScope(false), 
                                   editor instanceof EditorImpl ? ((EditorImpl)editor).myFractionalMetricsHintValue 
                                                                : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
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
    private boolean highlighted;
    private boolean current;

    private MyRenderer(Editor editor, String text, boolean animated) {
      updateState(editor, text, animated);
    }

    private String getText() {
      return myText;
    }

    public void update(Editor editor, String newText, boolean animated) {
      updateState(editor, newText, animated);
    }

    @Nullable
    @Override
    public String getContextMenuGroupId() {
      return "ParameterNameHints";
    }

    private void updateState(Editor editor, String text, boolean animated) {
      FontMetrics metrics = getFontMetrics(editor).metrics;
      startWidth = doCalcWidth(myText, metrics);
      myText = text;
      int endWidth = doCalcWidth(myText, metrics);
      steps = Math.max(1, Math.abs(endWidth - startWidth) / metrics.charWidth('a') / ANIMATION_CHARS_PER_STEP);
      step = animated ? 1 : steps + 1;
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
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      if (!(editor instanceof EditorImpl)) return;
      int ascent = ((EditorImpl)editor).getAscent();
      int descent = ((EditorImpl)editor).getDescent();
      Graphics2D g2d = (Graphics2D)g;
      if (myText != null && (step > steps || startWidth != 0)) {
        TextAttributes attributes = 
          editor.getColorsScheme().getAttributes(highlighted ? DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT_HIGHLIGHTED 
                                                             : DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT);
        if (attributes != null) {
          MyFontMetrics fontMetrics = getFontMetrics(editor);
          int gap = r.height < (fontMetrics.lineHeight + 2) ? 1 : 2;
          Color backgroundColor = attributes.getBackgroundColor();
          if (backgroundColor != null) {
            GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
            GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA);
            g.setColor(backgroundColor);
            g.fillRoundRect(r.x + 2, r.y + gap, r.width - 4, r.height - gap * 2, 8, 8);
            config.restore();
          }
          Color foregroundColor = attributes.getForegroundColor();
          if (foregroundColor != null) {
            Object savedHint = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            Shape savedClip = g.getClip();

            g.setColor(foregroundColor);
            g.setFont(getFont(editor));
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
            g.clipRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4);
            FontMetrics metrics = fontMetrics.metrics;
            g.drawString(myText, r.x + 7, r.y + Math.max(ascent, (r.height + metrics.getAscent() - metrics.getDescent()) / 2) - 1);
            
            g.setClip(savedClip);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint);

            if (current) {
              savedHint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
              g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
              g.drawRoundRect(r.x + 2, r.y + gap, r.width - 4, r.height - gap * 2, 8, 8);
              g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, savedHint);
            }
          }
        }
      }
      Color effectColor = textAttributes.getEffectColor();
      EffectType effectType = textAttributes.getEffectType();
      if (effectColor != null) {
        g.setColor(effectColor);
        int xStart = r.x;
        int xEnd = r.x + r.width;
        int y = r.y + ascent;
        Font font = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
        if (effectType == EffectType.LINE_UNDERSCORE) {
          EffectPainter.LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font);
        }
        else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
          EffectPainter.BOLD_LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font);
        }
        else if (effectType == EffectType.STRIKEOUT) {
          EffectPainter.STRIKE_THROUGH.paint(g2d, xStart, y, xEnd - xStart, ((EditorImpl)editor).getCharHeight(), font);
        }
        else if (effectType == EffectType.WAVE_UNDERSCORE) {
          EffectPainter.WAVE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font);
        }
        else if (effectType == EffectType.BOLD_DOTTED_LINE) {
          EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font);
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
