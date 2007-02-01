package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Factory which provides callbacks to run before and after checkin operations.
 *
 * @see com.intellij.openapi.vcs.ProjectLevelVcsManager#registerCheckinHandlerFactory(CheckinHandlerFactory)
 * @author lesya
 * @since 5.1
 */
public abstract class CheckinHandlerFactory {
  /**
   * Creates a handler for a single Checkin Project or Checkin File operation.
   *
   * @param panel the class which can be used to retrieve information about the files to be committed,
   *              and to get or set the commit message.
   * @return the handler instance.
   */
  @NotNull
  public abstract CheckinHandler createHandler(final CheckinProjectPanel panel);
}
