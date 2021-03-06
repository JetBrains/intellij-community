// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalSystemMessageEventImpl<T extends OperationDescriptor> extends BaseExternalSystemProgressEvent<T>
  implements ExternalSystemMessageEvent<T> {
  private final String myDescription;

  public ExternalSystemMessageEventImpl(@NotNull String eventId,
                                        @Nullable String parentEventId,
                                        @NotNull T descriptor,
                                        @Nullable String message) {
    super(eventId, parentEventId, descriptor);
    myDescription = message;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }
}
