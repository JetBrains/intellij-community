// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import org.jetbrains.annotations.ApiStatus;

/**
 * Implement this interface in the {@link BuildEvent} to customize its appearance on Build tool window.
 *
 * @see BuildEventPresentationData
 */
@ApiStatus.Experimental
public interface PresentableBuildEvent extends BuildEvent {
  BuildEventPresentationData getPresentationData();
}
