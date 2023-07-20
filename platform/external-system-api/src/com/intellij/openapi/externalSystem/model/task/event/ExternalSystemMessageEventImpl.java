// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task.event;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalSystemMessageEventImpl<T extends OperationDescriptor>
  extends BaseExternalSystemProgressEvent<T>
  implements ExternalSystemMessageEvent<T> {

  private final boolean myStdOut;
  private final @Nullable @Nls String myMessage;
  private final @Nullable String myDescription;

  public ExternalSystemMessageEventImpl(
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

  public ExternalSystemMessageEventImpl(@NotNull String eventId,
                                        @Nullable String parentEventId,
                                        @NotNull T descriptor,
                                        @Nullable @NlsSafe String description) {
    this(eventId, parentEventId, descriptor, true, description, description);
  }

  @Override
  public boolean isStdOut() {
    return myStdOut;
  }

  @Override
  public @Nullable String getMessage() {
    return myMessage;
  }

  @Override
  public @Nullable String getDescription() {
    return myDescription;
  }
}
