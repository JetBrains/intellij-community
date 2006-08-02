package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class TooltipController {
  private LightweightHint myCurrentTooltip;
  private TooltipRenderer myCurrentTooltipObject;
  private TooltipGroup myCurrentTooltipGroup;
  private Alarm myTooltipAlarm = new Alarm();

  public void cancelTooltips() {
    myTooltipAlarm.cancelAllRequests();
    hideCurrentTooltip();
  }

  public void cancelTooltip(TooltipGroup groupId) {
    if (groupId.equals(myCurrentTooltipGroup)) {
      cancelTooltips();
    }
  }

  public void showTooltipByMouseMove(final Editor editor,
                                     MouseEvent e,
                                     final TooltipRenderer tooltipObject,
                                     final boolean alignToRight, final TooltipGroup group) {
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
    showTooltip(editor, p, new LineTooltipRenderer(text), alignToRight, group);
  }
  public void showTooltip(final Editor editor, Point p, HighlightInfo info, boolean alignToRight, TooltipGroup group) {
    showTooltip(editor, p, new LineTooltipRenderer(info), alignToRight, group);
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
}