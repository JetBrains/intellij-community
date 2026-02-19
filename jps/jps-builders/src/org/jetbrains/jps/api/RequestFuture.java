// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@ApiStatus.Internal
public final class RequestFuture<T> extends BasicFuture<T> {
  private final T myHandler;
  private final UUID myRequestID;
  private final @Nullable CancelAction<T> myCancelAction;

  public interface CancelAction<T> {
    void cancel(RequestFuture<T> future) throws Exception;
  }

  public RequestFuture(T handler, @NotNull UUID requestID, @Nullable CancelAction<T> cancelAction) {
    super();
    myCancelAction = cancelAction;
    myHandler = handler;
    myRequestID = requestID;
  }

  public @NotNull UUID getRequestID() {
    return myRequestID;
  }

  public T getMessageHandler() {
    return myHandler;
  }

  @Override
  protected void performCancel() throws Exception {
    if (myCancelAction != null) {
      myCancelAction.cancel(this);
    }
  }
}
