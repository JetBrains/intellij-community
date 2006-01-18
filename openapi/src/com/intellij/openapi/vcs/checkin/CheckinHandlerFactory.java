package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import org.jetbrains.annotations.NotNull;

public abstract class CheckinHandlerFactory {
  public abstract
  @NotNull
  CheckinHandler createHandler(final CheckinProjectPanel panel);
}
