// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;

public final class DaemonTooltipUtil {
  private static final TooltipGroup DAEMON_INFO_GROUP = new TooltipGroup("DAEMON_INFO_GROUP", 0);

  public static void showInfoTooltip(HighlightInfo info, Editor editor, int defaultOffset) {
    EditorMouseHoverPopupManager.getInstance().showInfoTooltip(editor, info, defaultOffset, false, false);
  }

  public static void cancelTooltips() {
    TooltipController.getInstance().cancelTooltip(DAEMON_INFO_GROUP, null, true);
  }
}
