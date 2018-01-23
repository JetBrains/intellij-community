// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface MessageEventResult extends EventResult {
  MessageEvent.Kind getKind();

  @Nullable
  default String getDetails() { return null; }
}
