// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task.event;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * An event with textual description.
 * @param <T>
 */
public interface ExternalSystemMessageEvent<T extends OperationDescriptor> extends ExternalSystemProgressEvent<T> {

  default boolean isStdOut() { return true; }

  default @Nullable @Nls String getMessage() { return null; }

  /**
   * Textual description of event.
   *
   * @return arbitrary additional information about status update
   */
  default @Nullable String getDescription() { return null; }
}
