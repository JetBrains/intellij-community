// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface in the {@link BuildEvent} to customize its appearance on Build tool window.
 */
@Experimental
public interface PresentableBuildEvent extends BuildEvent {

  @NotNull BuildEventPresentationData getPresentationData();
}
