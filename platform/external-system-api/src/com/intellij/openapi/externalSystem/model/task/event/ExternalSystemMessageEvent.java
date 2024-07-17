// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task.event;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An event with textual description.
 * @param <T>
 */
public class ExternalSystemMessageEvent<T extends OperationDescriptor> extends ExternalSystemProgressEvent<T> {

  private final boolean myStdOut;
  private final @Nullable @Nls String myMessage;
  private final @Nullable String myDescription;

  public ExternalSystemMessageEvent(
    @NotNull String eventId,
    @Nullable String parentEventId,
    @NotNull T descriptor,
    boolean isStdOut,
    @Nullable @Nls String message,
    @Nullable String description
  ) {
    super(eventId, parentEventId, descriptor);
    myStdOut = isStdOut;
    myMessage = message;
    myDescription = description;
  }

  public ExternalSystemMessageEvent(@NotNull String eventId,
                                    @Nullable String parentEventId,
                                    @NotNull T descriptor,
                                    @Nullable @NlsSafe String description) {
    this(eventId, parentEventId, descriptor, true, description, description);
  }

  public boolean isStdOut() {
    return myStdOut;
  }

  public @Nullable String getMessage() {
    return myMessage;
  }

  /**
   * Textual description of the event.
   *
   * @return arbitrary additional information about status update
   */
  public @Nullable String getDescription() {
    return myDescription;
  }
}
