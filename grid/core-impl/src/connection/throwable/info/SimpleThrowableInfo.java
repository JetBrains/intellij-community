package com.intellij.database.connection.throwable.info;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SimpleThrowableInfo implements ThrowableInfo {
  private final @Nls String myMessage;
  private final Throwable myOriginalThrowable;

  SimpleThrowableInfo(@Nls @NotNull String message, @Nullable Throwable originalThrowable) {
    myMessage = message;
    myOriginalThrowable = originalThrowable;
  }

  SimpleThrowableInfo(@Nls @NotNull String message) {
    this(message, null);
  }

  SimpleThrowableInfo(@NotNull Throwable originalThrowable) {
    this(ThrowableInfoUtil.getDefaultMessage(originalThrowable), originalThrowable);
  }

  @Override
  public @NotNull @NlsContexts.NotificationContent @NlsContexts.DetailedDescription String getMessage() {
    return myMessage;
  }

  @Override
  public @Nullable Throwable getOriginalThrowable() {
    return myOriginalThrowable;
  }
}
