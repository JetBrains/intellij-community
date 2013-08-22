package com.intellij.execution.runners;

import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GotItMessage;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
* @author Sergey Simonchik
*/
public class RerunTestsNotification {

  private static final String KEY = "rerun.tests.notification.shown";

  public static void showRerunNotification(@Nullable RunContentDescriptor contentToReuse,
                                           @NotNull final ExecutionConsole executionConsole) {
    if (contentToReuse == null) {
      return;
    }
    String lastActionId = ActionManagerEx.getInstanceEx().getPrevPreformedActionId();
    boolean showNotification = !RerunTestsAction.ID.equals(lastActionId);
    if (showNotification && !PropertiesComponent.getInstance().isTrueValue(KEY)) {
      UiNotifyConnector.doWhenFirstShown(executionConsole.getComponent(), new Runnable() {
        @Override
        public void run() {
          doShow(executionConsole);
        }
      });
    }
  }

  private static void doShow(@NotNull final ExecutionConsole executionConsole) {
    final Alarm alarm = new Alarm();
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(
          ActionManager.getInstance().getAction(RerunTestsAction.ID)
        );
        if (shortcutText.isEmpty()) {
          return;
        }

        GotItMessage message = GotItMessage.createMessage("Press " + shortcutText + " to rerun tests", "");
        message.setDisposable(executionConsole);
        message.setCallback(new Runnable() {
          @Override
          public void run() {
            PropertiesComponent.getInstance().setValue(KEY, String.valueOf(true));
          }
        });
        message.setShowCallout(false);
        Dimension consoleSize = executionConsole.getComponent().getSize();

        message.show(
          new RelativePoint(
            executionConsole.getComponent(),
            new Point(consoleSize.width - 185, consoleSize.height - 60)
          ),
          Balloon.Position.below
        );

        Disposer.dispose(alarm);
      }
    }, 1000);
  }

}
