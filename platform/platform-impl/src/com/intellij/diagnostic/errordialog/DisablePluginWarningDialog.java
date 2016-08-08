package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Function;
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

  private static final int DISABLE_EXIT_CODE = OK_EXIT_CODE;
  private static final int DISABLE_AND_RESTART_EXIT_CODE = NEXT_USER_EXIT_CODE;
  private final boolean myRestartCapable;

  private DisablePluginWarningDialog(@NotNull Component parent, String pluginName, boolean hasDependants, boolean restartCapable) {
    super(parent, false);
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

  public static void disablePlugin(@NotNull PluginId pluginId, @NotNull JComponent parentComponent) {
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    final Ref<Boolean> hasDependants = new Ref<>(false);
    PluginManagerCore.checkDependants(plugin, pluginId1 -> PluginManager.getPlugin(pluginId1), pluginId12 -> {
      if (PluginManagerCore.CORE_PLUGIN_ID.equals(pluginId12.getIdString())) {
        return true;
      }
      hasDependants.set(true);
      return false;
    }
    );

    Application app = ApplicationManager.getApplication();
    DisablePluginWarningDialog d = new DisablePluginWarningDialog(parentComponent, plugin.getName(), hasDependants.get(), app.isRestartCapable());
    d.show();
    switch (d.getExitCode()) {
      case CANCEL_EXIT_CODE:
        return;
      case DISABLE_EXIT_CODE:
        PluginManagerCore.disablePlugin(pluginId.getIdString());
        break;
      case DISABLE_AND_RESTART_EXIT_CODE:
        PluginManagerCore.disablePlugin(pluginId.getIdString());
        app.restart();
        break;
    }
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
