// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface FileDownloadEvent extends ProgressBuildEvent {

  boolean isFirstInGroup();

  @NotNull String getDownloadPath();
}
