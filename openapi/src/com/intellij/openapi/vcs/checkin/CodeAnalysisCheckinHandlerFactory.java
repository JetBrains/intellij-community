package com.intellij.openapi.vcs.checkin;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vcs.CheckinProjectPanel;

/**
 * @author yole
 */
public class CodeAnalysisCheckinHandlerFactory extends CheckinHandlerFactory {
  @NotNull
  public CheckinHandler createHandler(final CheckinProjectPanel panel) {
    return new CodeAnalysisBeforeCheckinHandler(panel.getProject(), panel);
  }
}