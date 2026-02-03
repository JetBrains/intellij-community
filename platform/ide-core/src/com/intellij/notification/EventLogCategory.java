// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public abstract class EventLogCategory {
  public static final ExtensionPointName<EventLogCategory> EP_NAME = ExtensionPointName.create("com.intellij.eventLogCategory");

  private final @Nls String myDisplayName;

  protected EventLogCategory(@NotNull @Nls String displayName) {
    myDisplayName = displayName;
  }

  public final @NotNull @Nls String getDisplayName() {
    return myDisplayName;
  }

  public abstract boolean acceptsNotification(@NotNull String groupId);
}
