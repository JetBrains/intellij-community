// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class BrowseNotificationAction extends NotificationAction {
  private final String myUrl;

  public BrowseNotificationAction(@NotNull @NotificationContent String text, @NotNull String url) {
    super(text);
    myUrl = url;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
    IdeUiService.getInstance().browse(myUrl);
  }
}
