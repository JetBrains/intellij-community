// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

public class MacEmojiAndSymbolsInputAction extends DumbAwareAction {
  public MacEmojiAndSymbolsInputAction() {
    // it's not currently possible to use &, when text is set in resource bundle
    getTemplatePresentation().setText("Emoji & Symbols", false);
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
