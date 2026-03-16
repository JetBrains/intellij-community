// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.AppUIUtilKt;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Restarter;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Insets;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ResourceBundle;

@ApiStatus.Internal
public final class CreateDesktopEntryAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Disabled {
  private static final Logger LOG = Logger.getInstance(CreateDesktopEntryAction.class);

  public static boolean isAvailable() {
    return OS.isGenericUnix() &&!ExternalUpdateManager.isCreatingDesktopEntries() && PathEnvironmentVariableUtil.isOnPath("xdg-desktop-menu");
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabledAndVisible(isAvailable());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (!isAvailable()) return;

    var project = event.getProject();
    var dialog = new CreateDesktopEntryDialog(project);
    if (!dialog.showAndGet()) {
      return;
    }

    var globalEntry = dialog.myGlobalEntryCheckBox.isSelected();
    //noinspection UsagesOfObsoleteApi
    new Task.Backgroundable(project, ApplicationBundle.message("desktop.entry.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          createDesktopEntry(globalEntry);

          var title = IdeBundle.message("notification.title.desktop.entry.created");
          var message = ApplicationBundle.message("desktop.entry.success", ApplicationNamesInfo.getInstance().getProductName());
          new Notification("System Messages", title, message, NotificationType.INFORMATION).notify(getProject());
        }
        catch (Exception e) {
          reportFailure(e, getProject());
        }
      }
    }.queue();
  }

  public static @NotNull String getDesktopEntryName() {
    return AppUIUtil.INSTANCE.getFrameClass() + ".desktop";
  }

  public static void createDesktopEntry(boolean globalEntry) throws Exception {
    if (!isAvailable()) return;

    check();
    var entry = prepare();
    try {
      install(entry, globalEntry);
    }
    finally {
      Files.delete(entry);
    }
  }

  private static void reportFailure(Exception e, @Nullable Project project) {
    LOG.warn(e);
    var title = IdeBundle.message("notification.title.desktop.entry.creation.failed");
    var message = ExceptionUtil.getNonEmptyMessage(e, IdeBundle.message("notification.content.internal error"));
    new Notification("System Messages", title, message, NotificationType.ERROR).notify(project);
  }

  private static void check() throws ExecutionException, InterruptedException {
    int result = new GeneralCommandLine("which", "xdg-desktop-menu").createProcess().waitFor();
    if (result != 0) throw new RuntimeException(ApplicationBundle.message("desktop.entry.xdg.missing"));
  }

  private static Path prepare() throws IOException {
    var binDir = PathManager.getBinDir();
    assert Files.isDirectory(binDir) : "Invalid bin directory: '" + binDir + "'";

    var iconPath = AppUIUtilKt.findAppIcon();
    if (iconPath == null) throw new RuntimeException(ApplicationBundle.message("desktop.entry.icon.missing", binDir));

    var starter = Restarter.getIdeStarter();
    if (starter == null) throw new RuntimeException(ApplicationBundle.message("desktop.entry.script.missing", binDir));

    var names = ApplicationNamesInfo.getInstance();
    var name = names.getFullProductNameWithEdition();
    var content = ExecUtil.loadTemplate(CreateDesktopEntryAction.class.getClassLoader(), "entry.desktop", Map.of(
      "$NAME$", name,
      "$SCRIPT$", StringUtil.wrapWithDoubleQuote(starter.toString()),
      "$ICON$", iconPath,
      "$COMMENT$", StringUtil.notNullize(names.getMotto(), name),
      "$WM_CLASS$", AppUIUtil.INSTANCE.getFrameClass()));
    var entryFile = PathManager.getTempDir().resolve(getDesktopEntryName());
    Files.writeString(entryFile, content);
    return entryFile;
  }

  private static void install(Path entryFile, boolean globalEntry) throws IOException, ExecutionException {
    if (globalEntry) {
      var script = ExecUtil.createTempExecutableScript(
        "create_desktop_entry_", ".sh",
        "#!/bin/sh\n" +
        "xdg-desktop-menu install --mode system '" + entryFile + "' && xdg-desktop-menu forceupdate --mode system\n");
      try {
        exec(new GeneralCommandLine(script.getPath()), ApplicationBundle.message("desktop.entry.sudo.prompt"));
      }
      finally {
        Files.delete(script.toPath());
      }
    }
    else {
      exec(new GeneralCommandLine("xdg-desktop-menu", "install", "--mode", "user", entryFile.toString()), null);
      exec(new GeneralCommandLine("xdg-desktop-menu", "forceupdate", "--mode", "user"), null);
    }
  }

  private static void exec(GeneralCommandLine command, @Nls @Nullable String prompt) throws IOException, ExecutionException {
    command.withRedirectErrorStream(true);
    var result = new CapturingProcessHandler(prompt != null ? ExecUtil.sudoCommand(command, prompt) : command).runProcess();
    var exitCode = result.getExitCode();
    if (exitCode != 0) {
      var message = "Command '" + (prompt != null ? "sudo " : "") + command.getCommandLineString() + "' returned " + exitCode;
      var output = result.getStdout();
      if (!output.isBlank()) message += "\nOutput: " + output.trim();
      throw new RuntimeException(message);
    }
  }

  public static final class CreateDesktopEntryDialog extends DialogWrapper {
    private static final @NlsSafe String APP_NAME_PLACEHOLDER = "$APP_NAME$";

    private final JPanel myContentPane;
    private final JLabel myLabel;
    private final JCheckBox myGlobalEntryCheckBox;

    public CreateDesktopEntryDialog(Project project) {
      super(project);
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myContentPane = new JPanel();
        myContentPane.setLayout(new GridLayoutManager(3, 1, new Insets(10, 10, 10, 10), -1, -1));
        myGlobalEntryCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(myGlobalEntryCheckBox, this.$$$getMessageFromBundle$$$("messages/IdeBundle",
                                                                                         "checkbox.create.the.entry.for.all.users.requires.superuser.privileges"));
        myContentPane.add(myGlobalEntryCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 1, false));
        final Spacer spacer1 = new Spacer();
        myContentPane.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                       GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myLabel = new JLabel();
        this.$$$loadLabelText$$$(myLabel, this.$$$getMessageFromBundle$$$("messages/IdeBundle", "label.you.can.create.a.desktop.entry"));
        myContentPane.add(myLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
      }
      init();
      setTitle(ApplicationBundle.message("desktop.entry.title"));
      myLabel.setText(myLabel.getText().replace(APP_NAME_PLACEHOLDER, ApplicationNamesInfo.getInstance().getProductName()));
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    private void $$$loadLabelText$$$(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myContentPane; }

    @Override
    protected JComponent createCenterPanel() {
      return myContentPane;
    }
  }
}
