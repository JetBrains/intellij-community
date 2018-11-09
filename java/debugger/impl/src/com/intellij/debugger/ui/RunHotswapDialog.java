package com.intellij.debugger.ui;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface RunHotswapDialog {

  Collection<DebuggerSession> getSessionsToReload();

  boolean showAndGet();

  class Factory {
    public static RunHotswapDialog createDialog(Project project, List<DebuggerSession> sessions, boolean displayHangWarning) {
      if (ApplicationManager.getApplication().isOnAir()) {
        return new RunHotswapDialog() {
          @Override
          public Collection<DebuggerSession> getSessionsToReload() {
            return sessions;
          }

          @Override
          public boolean showAndGet() {
            return Messages.showDialog(project, "Hotswap?", "Hotswap", new String[]{"Yes", "No"}, 0, null, new DialogWrapper.DoNotAskOption() {

              @Override
              public boolean isToBeShown() {
                return DebuggerSettings.RUN_HOTSWAP_ASK.equals(DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE);
              }
              @Override
              public void setToBeShown(boolean toBeShown, int exitCode) {
                if (toBeShown) {
                  DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK;
                }
                else {
                  if (exitCode == 0) {
                    DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ALWAYS;
                  }
                  else {
                    DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER;
                  }
                }
              }

              @Override
              public boolean canBeHidden() {
                return false;
              }

              @Override
              public boolean shouldSaveOptionsOnCancel() {
                return true;
              }

              @NotNull
              @Override
              public String getDoNotShowMessage() {
                return "Remember";
              }
            }) == 0;
          }
        };
      }
      else {
        return new RunHotswapDialogImpl(project, sessions, displayHangWarning);
      }
    }
  }
}
