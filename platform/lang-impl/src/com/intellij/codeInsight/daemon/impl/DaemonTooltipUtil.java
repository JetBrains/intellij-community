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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.HintHint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class DaemonTooltipUtil {
  private static final TooltipGroup DAEMON_INFO_GROUP = new TooltipGroup("DAEMON_INFO_GROUP", 0);

  public static void showInfoTooltip(HighlightInfo info, Editor editor, int defaultOffset) {
    showInfoTooltip(info, editor, defaultOffset, -1);
  }

  public static void cancelTooltips() {
    TooltipController.getInstance().cancelTooltip(DAEMON_INFO_GROUP, null, true);
  }

  public static void showInfoTooltip(@NotNull final HighlightInfo info, @NotNull Editor editor, final int defaultOffset, final int currentWidth) {
    if (info.getToolTip() == null) return;
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();

    Point bestPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(defaultOffset));
    bestPoint.y += editor.getLineHeight() / 2;

    if (!visibleArea.contains(bestPoint)) {
      bestPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(defaultOffset));
    }

    Point p = SwingUtilities.convertPoint(
      editor.getContentComponent(),
      bestPoint,
      editor.getComponent().getRootPane().getLayeredPane()
    );

    TooltipController.getInstance().showTooltip(editor, p, info.getToolTip(), currentWidth, false, DAEMON_INFO_GROUP, new HintHint(editor, bestPoint).setAwtTooltip(true).setHighlighterType(true).setCalloutShift(
      editor.getLineHeight() / 2 - 1));
  }
}
