// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.DiskQueryRelay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public class RefCardAction extends DumbAwareAction {
  private final NullableLazyValue<Path> myRefCardPath = NullableLazyValue.volatileLazyNullable(() -> {
    var file = Path.of(PathManager.getHomePath() + "/help/ReferenceCard" + (SystemInfo.isMac ? "ForMac" : "") + ".pdf");
    return DiskQueryRelay.compute(() -> Files.exists(file) ? file : null);
  });

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(myRefCardPath.getValue() != null || getKeymapUrl() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var file = myRefCardPath.getValue();
    if (file != null) {
      BrowserUtil.browse(file);
    }
    else {
      var url = getKeymapUrl();
      if (url != null) {
        BrowserUtil.browse(url);
      }
    }
  }

  private static @Nullable String getKeymapUrl() {
    var appInfo = ApplicationInfoEx.getInstanceEx();
    return SystemInfo.isMac ? appInfo.getMacKeymapUrl() : appInfo.getWinKeymapUrl();
  }
}
