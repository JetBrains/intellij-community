// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import org.jetbrains.annotations.NotNull;

public interface FileDownloadedEvent extends BuildEvent {

  long getDuration();

  @NotNull String getDownloadPath();
}
