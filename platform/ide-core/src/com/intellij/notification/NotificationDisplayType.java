// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.PropertyKey;

public enum NotificationDisplayType {
  NONE("notification.type.no.popup"),
  /** Expires automatically after 10 seconds. */
  BALLOON("notification.type.balloon"),
  /** Needs to be closed by user. */
  STICKY_BALLOON("notification.type.sticky.balloon"),
  TOOL_WINDOW("notification.type.tool.window.balloon");

  private final String myKey;

  NotificationDisplayType(@PropertyKey(resourceBundle = IdeCoreBundle.BUNDLE) String key) {
    myKey = key;
  }

  public @NlsContexts.ListItem String getTitle() {
    return IdeCoreBundle.message(myKey);
  }
}
