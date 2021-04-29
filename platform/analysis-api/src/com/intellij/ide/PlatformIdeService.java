// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class PlatformIdeService {
  public void browseHyperlinkEvent(HyperlinkEvent event) {
  }

  public static PlatformIdeService getInstance() {
    return ApplicationManager.getApplication().getService(PlatformIdeService.class);
  }

  public void warningNotification(@NotNull @NonNls String groupId,
                                  @Nullable Icon icon,
                                  @Nullable @NlsContexts.NotificationTitle String title,
                                  @Nullable @NlsContexts.NotificationSubtitle String subtitle,
                                  @Nullable @NlsContexts.NotificationContent String content) {}
}
