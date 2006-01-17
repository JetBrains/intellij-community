package com.intellij.openapi.vcs.checkin;

import org.jetbrains.annotations.NotNull;

public abstract class CheckinHandlerFactory {
  public abstract @NotNull CheckinHandler createHandler();
}
