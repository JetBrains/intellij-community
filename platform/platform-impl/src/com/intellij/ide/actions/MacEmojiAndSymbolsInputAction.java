// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

final class MacEmojiAndSymbolsInputAction extends DumbAwareAction {
  public MacEmojiAndSymbolsInputAction() {
    getTemplatePresentation().setText(ActionsBundle.message("EmojiAndSymbols.text"), false);
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(SystemInfo.isMac);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!SystemInfo.isMac) return;
    Foundation.executeOnMainThread(false, false, () -> {
      ID app = Foundation.invoke("NSApplication", "sharedApplication");
      Foundation.invoke(app, "orderFrontCharacterPalette:", (Object)null);
    });
  }
}
