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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class TooltipController {
  private LightweightHint myCurrentTooltip;
  private TooltipRenderer myCurrentTooltipObject;
  private TooltipGroup myCurrentTooltipGroup;
  private final Alarm myTooltipAlarm = new Alarm();

  public static TooltipController getInstance() {
    return ServiceManager.getService(TooltipController.class);
  }

  public void cancelTooltips() {
    myTooltipAlarm.cancelAllRequests();
    hideCurrentTooltip();
  }

  public void cancelTooltip(TooltipGroup groupId) {
    if (groupId.equals(myCurrentTooltipGroup)) {
      cancelTooltips();
    }
  }

  public void showTooltipByMouseMove(@NotNull final Editor editor,
                                     @NotNull MouseEvent e,
                                     final TooltipRenderer tooltipObject,
                                     final boolean alignToRight,
                                     @NotNull final TooltipGroup group) {
    myTooltipAlarm.cancelAllRequests();
    if (myCurrentTooltip == null || !myCurrentTooltip.isVisible()) {
      myCurrentTooltipObject = null;
    }

    if (Comparing.equal(tooltipObject, myCurrentTooltipObject)) {
      return;
    }
    hideCurrentTooltip();

    if (tooltipObject != null) {
      final Point p = SwingUtilities.convertPoint(
        (Component)e.getSource(),
        e.getPoint(),
        editor.getComponent().getRootPane().getLayeredPane()
      );
      p.x += alignToRight ? -10 : 10;

      myTooltipAlarm.addRequest(
        new Runnable() {
          public void run() {
            Project project = editor.getProject();
            if (project != null && !project.isOpen()) return;
            if (editor.getContentComponent().isShowing()) {
              showTooltip(editor, p, tooltipObject, alignToRight, group);
            }
          }
        },
        50
      );
    }
  }

  private void hideCurrentTooltip() {
    if (myCurrentTooltip != null) {
      myCurrentTooltip.hide();
      myCurrentTooltip = null;
      myCurrentTooltipGroup = null;
    }
  }

  public void showTooltip(final Editor editor, Point p, String text, boolean alignToRight, TooltipGroup group) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group);
  }

  public void showTooltip(final Editor editor, Point p, String text, int currentWidth, boolean alignToRight, TooltipGroup group) {
    TooltipRenderer tooltipRenderer = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(text, currentWidth);
    showTooltip(editor, p, tooltipRenderer, alignToRight, group);
  }

  public void showTooltip(final Editor editor, Point p, TooltipRenderer tooltipRenderer, boolean alignToRight, TooltipGroup group) {
    myTooltipAlarm.cancelAllRequests();
    if (myCurrentTooltip == null || !myCurrentTooltip.isVisible()) {
      myCurrentTooltipObject = null;
    }

    if (Comparing.equal(tooltipRenderer, myCurrentTooltipObject)) return;
    if (myCurrentTooltipGroup != null && group.compareTo(myCurrentTooltipGroup) < 0) return;

    p = new Point(p);
    hideCurrentTooltip();

    LightweightHint hint = tooltipRenderer.show(editor, p, alignToRight, group);

    myCurrentTooltipGroup = group;
    myCurrentTooltip = hint;
    myCurrentTooltipObject = tooltipRenderer;
  }

  public boolean shouldSurvive(final MouseEvent e) {
    if (myCurrentTooltip != null) {
      final Point pointOnComponent = new RelativePoint(e).getPointOn(myCurrentTooltip.getComponent()).getPoint();
      final Rectangle bounds = myCurrentTooltip.getBounds();
      if (bounds.x - 10 < pointOnComponent.x && bounds.width + bounds.x + 10 > pointOnComponent.x) {//do not hide hovered tooltip
        if (bounds.y - 10 < pointOnComponent.y && bounds.y + bounds.height + 10 > pointOnComponent.y) {
          return true;
        }
      }
    }
    return false;
  }
}
