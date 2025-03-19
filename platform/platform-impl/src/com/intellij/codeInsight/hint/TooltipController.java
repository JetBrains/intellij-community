// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

@Service
public final class TooltipController {
  private LightweightHint myCurrentTooltip;
  private TooltipRenderer myCurrentTooltipObject;
  private TooltipGroup myCurrentTooltipGroup;

  public static TooltipController getInstance() {
    return ApplicationManager.getApplication().getService(TooltipController.class);
  }

  public void cancelTooltips() {
    hideCurrentTooltip();
  }

  public void cancelTooltip(@NotNull TooltipGroup groupId, MouseEvent mouseEvent, boolean forced) {
    if (groupId.equals(myCurrentTooltipGroup) && (forced || !shouldSurvive(mouseEvent))) {
      cancelTooltips();
    }
  }

  /**
   * Returns newly created hint, or already existing (for the same renderer)
   */
  public @Nullable LightweightHint showTooltipByMouseMove(@NotNull Editor editor,
                                                          @NotNull RelativePoint point,
                                                          @NotNull TooltipRenderer tooltipObject,
                                                          boolean alignToRight,
                                                          @NotNull TooltipGroup group,
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

    if (tooltipObject.equals(myCurrentTooltipObject)) {
      IdeTooltipManager.getInstance().cancelAutoHide();
      return myCurrentTooltip;
    }
    hideCurrentTooltip();

    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane == null) {
      return null;
    }
    Point p = point.getPointOn(rootPane.getLayeredPane()).getPoint();
    if (!hintHint.isAwtTooltip()) {
      p.x += alignToRight ? -10 : 10;
    }

    Project project = editor.getProject();
    if (project != null && !project.isOpen()) return null;
    if (editor.getContentComponent().isShowing()) {
      return doShowTooltip(editor, p, tooltipObject, alignToRight, group, hintHint);
    }
    return null;
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

  public void showTooltip(@NotNull Editor editor,
                          @NotNull Point p,
                          @NotNull @NlsContexts.Tooltip String text,
                          boolean alignToRight,
                          @NotNull TooltipGroup group) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group);
  }

  public void showTooltip(@NotNull Editor editor,
                          @NotNull Point p,
                          @NotNull @NlsContexts.Tooltip String text,
                          int currentWidth,
                          boolean alignToRight,
                          @NotNull TooltipGroup group) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text, currentWidth);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group);
  }

  public void showTooltip(@NotNull Editor editor,
                          @NotNull Point p,
                          @NotNull @NlsContexts.Tooltip String text,
                          int currentWidth,
                          boolean alignToRight,
                          @NotNull TooltipGroup group,
                          @NotNull HintHint hintHint) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text, currentWidth);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group, hintHint);
  }

  public void showTooltip(@NotNull Editor editor,
                          @NotNull Point p,
                          @NotNull TooltipRenderer tooltipRenderer,
                          boolean alignToRight,
                          @NotNull TooltipGroup group) {
    showTooltip(editor, p, tooltipRenderer, alignToRight, group, new HintHint(editor, p));
  }

  public void showTooltip(@NotNull Editor editor,
                          @NotNull Point p,
                          @NotNull TooltipRenderer tooltipRenderer,
                          boolean alignToRight,
                          @NotNull TooltipGroup group,
                          @NotNull HintHint hintInfo) {
    doShowTooltip(editor, p, tooltipRenderer, alignToRight, group, hintInfo);
  }

  private @Nullable LightweightHint doShowTooltip(@NotNull Editor editor,
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
      return null;
    }
    if (myCurrentTooltipGroup != null && group.compareTo(myCurrentTooltipGroup) < 0) return null;

    p = new Point(p);
    hideCurrentTooltip();

    LightweightHint hint = tooltipRenderer.show(editor, p, alignToRight, group, hintInfo);

    myCurrentTooltipGroup = group;
    myCurrentTooltip = hint;
    myCurrentTooltipObject = tooltipRenderer;

    return hint;
  }

  public boolean shouldSurvive(MouseEvent e) {
    return myCurrentTooltip != null && myCurrentTooltip.canControlAutoHide();
  }

  public void hide(@NotNull LightweightHint lightweightHint) {
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
