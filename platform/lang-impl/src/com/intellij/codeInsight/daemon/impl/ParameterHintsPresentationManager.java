// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ParameterHintsPresentationManager implements Disposable {
  private static final Key<AnimationStep> ANIMATION_STEP = Key.create("ParameterHintAnimationStep");

  private static final int ANIMATION_STEP_MS = 25;
  private static final int ANIMATION_CHARS_PER_STEP = 3;

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

  private static class MyRenderer extends HintRenderer {
    private int startWidth;
    private int steps;
    private int step;
    private boolean highlighted;
    private boolean current;

    private MyRenderer(Editor editor, String text, boolean animated) {
      super(text);
      updateState(editor, text, animated);
    }

    public void update(Editor editor, String newText, boolean animated) {
      updateState(editor, newText, animated);
    }

    @Nullable
    @Override
    protected TextAttributes getTextAttributes(@NotNull Editor editor) {
      if (step > steps || startWidth != 0) {
        return editor.getColorsScheme().getAttributes(current 
                                                      ? DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT_CURRENT 
                                                      : highlighted ? DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT_HIGHLIGHTED
                                                                    : DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT);
      }
      return null;
    }

    @Nullable
    @Override
    public String getContextMenuGroupId() {
      return "ParameterNameHints";
    }

    private void updateState(Editor editor, String text, boolean animated) {
      FontMetrics metrics = getFontMetrics(editor).getMetrics();
      startWidth = doCalcWidth(getText(), metrics);
      setText(text);
      int endWidth = doCalcWidth(getText(), metrics);
      steps = Math.max(1, Math.abs(endWidth - startWidth) / metrics.charWidth('a') / ANIMATION_CHARS_PER_STEP);
      step = animated ? 1 : steps + 1;
    }
    
    public boolean nextStep() {
      return ++step <= steps;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      int endWidth = super.calcWidthInPixels(editor);
      return step <= steps ? Math.max(1, startWidth + (endWidth - startWidth) / steps * step) : endWidth;
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
