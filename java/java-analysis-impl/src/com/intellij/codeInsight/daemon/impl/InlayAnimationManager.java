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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class InlayAnimationManager {
  private static final int ANIMATION_STEP_MS = 50;
  private static final int ANIMATION_STEPS = 4;
  private static final Alarm OUR_ALARM = new Alarm();

  public static void addElementWithAnimation(@NotNull Editor editor, int offset, @NotNull Inlay.Renderer renderer, int startWidth) {
    AnimationStepRenderer stepRenderer = new AnimationStepRenderer(editor, renderer, startWidth);
    Inlay inlay = editor.getInlayModel().addElement(offset, Inlay.Type.INLINE, stepRenderer);
    if (inlay != null) {
      OUR_ALARM.addRequest(new AnimationStep(editor, inlay, stepRenderer), ANIMATION_STEP_MS, ModalityState.any());
    }
  }

  private static class AnimationStep implements Runnable {
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
        OUR_ALARM.addRequest(new AnimationStep(myEditor, newInlay, (AnimationStepRenderer)nextRenderer), ANIMATION_STEP_MS, ModalityState.any());
      }
    }
  }

  private static class AnimationStepRenderer extends Inlay.Renderer {
    private final Inlay.Renderer renderer;
    private final int startWidth;
    private final int endWidth;
    private final int step;

    private AnimationStepRenderer(Editor editor, Inlay.Renderer renderer, int startWidth) {
      this(renderer, startWidth, renderer.calcWidthInPixels(editor), 1);
    }

    private AnimationStepRenderer(Inlay.Renderer renderer, int startWidth, int endWidth, int step) {
      this.renderer = renderer;
      this.startWidth = startWidth;
      this.endWidth = endWidth;
      this.step = step;
    }

    private Inlay.Renderer nextStep() {
      return step < ANIMATION_STEPS ? new AnimationStepRenderer(renderer, startWidth, endWidth, step + 1) : renderer;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      return Math.max(1, startWidth + (endWidth - startWidth) / ANIMATION_STEPS * step);
    }

    @Override
    public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
      if (startWidth > 0) {
        renderer.paint(g, r, editor);
      }
    }
  }
}
