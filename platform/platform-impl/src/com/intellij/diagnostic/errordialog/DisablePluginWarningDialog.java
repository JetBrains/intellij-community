package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author ksafonov
 */
public class DisablePluginWarningDialog extends DialogWrapper {
  private JLabel myPromptLabel;
  private JLabel myRestartLabel;
  private JPanel myContentPane;

  public static final int DISABLE_EXIT_CODE = OK_EXIT_CODE;
  public static final int DISABLE_AND_RESTART_EXIT_CODE = NEXT_USER_EXIT_CODE;
  private final boolean myRestartCapable;

  public DisablePluginWarningDialog(Component c, String pluginName, boolean hasDependants, boolean restartCapable) {
    super(c, false);
    myRestartCapable = restartCapable;
    myPromptLabel.setText(
      DiagnosticBundle.message(hasDependants ? "error.dialog.disable.plugin.prompt.dependants" : "error.dialog.disable.plugin.prompt",
                               pluginName));
    myRestartLabel
      .setText(DiagnosticBundle.message(restartCapable ? "error.dialog.disable.plugin.restart" : "error.dialog.disable.plugin.norestart",
                                        ApplicationNamesInfo.getInstance().getFullProductName()));

    setTitle(DiagnosticBundle.message("error.dialog.disable.plugin.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    if (SystemInfo.isMac) {
      if (myRestartCapable) {
        return new Action[]{getCancelAction(), new DisableAction(), new DisableAndRestartAction()};
      }
      else {
        return new Action[]{getCancelAction(), new DisableAction()};
      }
    }
    else {
      if (myRestartCapable) {
        return new Action[]{new DisableAction(), new DisableAndRestartAction(), getCancelAction()};
      }
      else {
        return new Action[]{new DisableAction(), getCancelAction()};
      }
    }
  }

  private class DisableAction extends DialogWrapperAction {
    protected DisableAction() {
      super(DiagnosticBundle.message("error.dialog.disable.plugin.action.disable"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      close(DISABLE_EXIT_CODE);
    }
  }

  private class DisableAndRestartAction extends DialogWrapperAction {
    protected DisableAndRestartAction() {
      super(DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      close(DISABLE_AND_RESTART_EXIT_CODE);
    }
  }
}
