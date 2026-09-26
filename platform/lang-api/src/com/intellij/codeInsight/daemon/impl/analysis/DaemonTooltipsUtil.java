// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

public final class DaemonTooltipsUtil {
  public static @NlsSafe @NotNull String getWrappedTooltip(String message,
                                                           String shortName,
                                                           boolean showToolDescription) {
    return getWrappedTooltip(message, shortName, getShortcutText(), showToolDescription);
  }

  public static @NlsSafe @NotNull String getWrappedTooltip(String message,
                                                           String shortName,
                                                           String shortcutText,
                                                           boolean showToolDescription) {
    return getWrappedTooltipWithCustomReference(message, "#inspection/" + shortName, shortcutText, showToolDescription);
  }

  public static @NlsSafe @NotNull String getWrappedTooltipWithCustomReference(String message, String reference, boolean showToolDescription) {
    return getWrappedTooltipWithCustomReference(message, reference, getShortcutText(), showToolDescription);
  }

  public static @NlsSafe @NotNull String getWrappedTooltipWithCustomReference(String message, String reference, String shortcutText,
                                                                              boolean showToolDescription) {
    String link = "";
    if (showToolDescription) {
      link = " <a "
             + "href=\"" + reference + "\""
             + (StartupUiUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "")
             + ">" + DaemonBundle.message("inspection.extended.description")
             + "</a> " + shortcutText;
    }
    return XmlStringUtil.wrapInHtml((message.startsWith("<html>") ? XmlStringUtil.stripHtml(message)
                                                                  : XmlStringUtil.escapeString(message)) + link);
  }

  public static @NotNull String getShortcutText() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) {
      return "";
    }
    return "(" + KeymapUtil.getShortcutsText(keymapManager.getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
  }
}
