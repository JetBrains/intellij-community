/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;

public class TooltipController {
  private LightweightHint myCurrentTooltip;
  private TooltipRenderer myCurrentTooltipObject;
  private TooltipGroup myCurrentTooltipGroup;

  public static TooltipController getInstance() {
    return ServiceManager.getService(TooltipController.class);
  }

  public void cancelTooltips() {
    hideCurrentTooltip();
  }

  public void cancelTooltip(@NotNull TooltipGroup groupId, MouseEvent mouseEvent, boolean forced) {
    if (groupId.equals(myCurrentTooltipGroup)) {
      if (!forced && myCurrentTooltip != null && myCurrentTooltip.canControlAutoHide()) return;

      cancelTooltips();
    }
  }

  public void showTooltipByMouseMove(@NotNull final Editor editor,
                                     @NotNull final RelativePoint point,
                                     final TooltipRenderer tooltipObject,
                                     final boolean alignToRight,
                                     @NotNull final TooltipGroup group,
                                     @NotNull HintHint hintHint) {
    LightweightHint currentTooltip = myCurrentTooltip;
    if (currentTooltip == null || !currentTooltip.isVisible()) {
      if (currentTooltip != null) {
        if (!IdeTooltipManager.getInstance().isQueuedToShow(currentTooltip.getCurrentIdeTooltip())) {
          myCurrentTooltipObject = null;
        }
      }
      else {
        myCurrentTooltipObject = null;
      }
    }

    if (Comparing.equal(tooltipObject, myCurrentTooltipObject)) {
      IdeTooltipManager.getInstance().cancelAutoHide();
      return;
    }
    hideCurrentTooltip();

    if (tooltipObject != null) {
      final Point p = point.getPointOn(editor.getComponent().getRootPane().getLayeredPane()).getPoint();
      if (!hintHint.isAwtTooltip()) {
        p.x += alignToRight ? -10 : 10;
      }

      Project project = editor.getProject();
      if (project != null && !project.isOpen()) return;
      if (editor.getContentComponent().isShowing()) {
        showTooltip(editor, p, tooltipObject, alignToRight, group, hintHint);
      }
    }
  }

  private void hideCurrentTooltip() {
    if (myCurrentTooltip != null) {
      LightweightHint currentTooltip = myCurrentTooltip;
      myCurrentTooltip = null;
      currentTooltip.hide();
      myCurrentTooltipGroup = null;
      IdeTooltipManager.getInstance().hide(null);
    }
  }

  public void showTooltip(@NotNull Editor editor, @NotNull Point p, @NotNull String text, boolean alignToRight, @NotNull TooltipGroup group) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group);
  }

  public void showTooltip(@NotNull Editor editor, @NotNull Point p, @NotNull String text, int currentWidth, boolean alignToRight, @NotNull TooltipGroup group) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text, currentWidth);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group);
  }

  public void showTooltip(@NotNull Editor editor, @NotNull Point p, @NotNull String text, int currentWidth, boolean alignToRight, @NotNull TooltipGroup group, @NotNull HintHint hintHint) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text, currentWidth);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group, hintHint);
  }

  public void showTooltip(@NotNull Editor editor, @NotNull Point p, @NotNull TooltipRenderer tooltipRenderer, boolean alignToRight, @NotNull TooltipGroup group) {
    showTooltip(editor, p, tooltipRenderer, alignToRight, group, new HintHint(editor, p));
  }

  public void showTooltip(@NotNull Editor editor,
                          @NotNull Point p,
                          @NotNull TooltipRenderer tooltipRenderer,
                          boolean alignToRight,
                          @NotNull TooltipGroup group,
                          @NotNull HintHint hintInfo) {
    if (myCurrentTooltip == null || !myCurrentTooltip.isVisible()) {
      myCurrentTooltipObject = null;
    }

    if (Comparing.equal(tooltipRenderer, myCurrentTooltipObject)) {
      IdeTooltipManager.getInstance().cancelAutoHide();
      return;
    }
    if (myCurrentTooltipGroup != null && group.compareTo(myCurrentTooltipGroup) < 0) return;

    p = new Point(p);
    hideCurrentTooltip();

    LightweightHint hint = tooltipRenderer.show(editor, p, alignToRight, group, hintInfo);

    myCurrentTooltipGroup = group;
    myCurrentTooltip = hint;
    myCurrentTooltipObject = tooltipRenderer;
  }

  public boolean shouldSurvive(final MouseEvent e) {
    if (myCurrentTooltip != null) {
      if (myCurrentTooltip.canControlAutoHide()) return true;
    }
    return false;
  }

  public void hide(LightweightHint lightweightHint) {
    if (myCurrentTooltip != null && myCurrentTooltip.equals(lightweightHint)) {
      hideCurrentTooltip();
    }
  }

  public void resetCurrent() {
    myCurrentTooltip = null;
    myCurrentTooltipGroup = null;
    myCurrentTooltipObject = null;
  }
}
