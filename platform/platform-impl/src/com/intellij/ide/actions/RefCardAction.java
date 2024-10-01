// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

@ApiStatus.Internal
public final class RefCardAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
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
    e.getPresentation().setEnabledAndVisible(myRefCardPath.getValue() != null || ExternalProductResourceUrls.getInstance().getKeyboardShortcutsPdfUrl() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var file = myRefCardPath.getValue();
    if (file != null) {
      BrowserUtil.browse(file);
    }
    else {
      var url = ExternalProductResourceUrls.getInstance().getKeyboardShortcutsPdfUrl();
      if (url != null) {
        BrowserUtil.browse(url.toExternalForm());
      }
    }
  }
}
