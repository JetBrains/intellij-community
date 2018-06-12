// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.containers.ContainerUtil.newHashMap;

public class CreateDesktopEntryAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateDesktopEntryAction");

  private static final NullableLazyValue<String> ourScript = new AtomicNullableLazyValue<String>() {
    @Nullable
    @Override
    protected String compute() {
      String binPath = PathManager.getBinPath();
      ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();

      String execPath = binPath + '/' + names.getProductName() + ".sh";
      if (new File(execPath).canExecute()) return execPath;

      execPath = binPath + '/' + names.getProductName().toLowerCase(Locale.US) + ".sh";
      if (new File(execPath).canExecute()) return execPath;

      execPath = binPath + '/' + names.getScriptName() + ".sh";
      if (new File(execPath).canExecute()) return execPath;

      return null;
    }
  };

  public static boolean isAvailable() {
    return SystemInfo.isUnix && !PathManager.isSnap() && SystemInfo.hasXdgOpen();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = isAvailable();
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (!isAvailable()) return;

    Project project = event.getProject();
    CreateDesktopEntryDialog dialog = new CreateDesktopEntryDialog(project);
    if (!dialog.showAndGet()) {
      return;
    }

    boolean globalEntry = dialog.myGlobalEntryCheckBox.isSelected();
    new Task.Backgroundable(project, ApplicationBundle.message("desktop.entry.title")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          createDesktopEntry(globalEntry);

          String message = ApplicationBundle.message("desktop.entry.success", ApplicationNamesInfo.getInstance().getProductName());
          Notifications.Bus.notify(
            new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Desktop Entry Created", message, NotificationType.INFORMATION),
            getProject());
        }
        catch (Exception e) {
          reportFailure(e, getProject());
        }
      }
    }.queue();
  }

  public static void createDesktopEntry(boolean globalEntry) throws Exception {
    if (!isAvailable()) return;

    File entry = null;
    try {
      check();
      entry = prepare();
      install(entry, globalEntry);
    }
    finally {
      if (entry != null) {
        FileUtil.delete(entry);
      }
    }
  }

  public static void reportFailure(@NotNull Exception e, @Nullable final Project project) {
    LOG.warn(e);
    final String message = ExceptionUtil.getNonEmptyMessage(e, "Internal error");
    Notifications.Bus.notify(
      new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Desktop Entry Creation Failed", message, NotificationType.ERROR),
      project);
  }

  private static void check() throws ExecutionException, InterruptedException {
    int result = new GeneralCommandLine("which", "xdg-desktop-menu").createProcess().waitFor();
    if (result != 0) throw new RuntimeException(ApplicationBundle.message("desktop.entry.xdg.missing"));
  }

  private static File prepare() throws IOException {
    String binPath = PathManager.getBinPath();
    assert new File(binPath).isDirectory() : "Invalid bin path: '" + binPath + "'";

    String iconPath = AppUIUtil.findIcon(binPath);
    if (iconPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.icon.missing", binPath));
    }

    String execPath = ourScript.getValue();
    if (execPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.script.missing", binPath));
    }
    execPath = StringUtil.wrapWithDoubleQuote(execPath);

    ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();

    String name = names.getFullProductNameWithEdition();
    String comment = names.getMotto();
    String wmClass = AppUIUtil.getFrameClass();
    Map<String, String> vars = newHashMap(pair("$NAME$", name),
                                          pair("$SCRIPT$", execPath),
                                          pair("$ICON$", iconPath),
                                          pair("$COMMENT$", comment),
                                          pair("$WM_CLASS$", wmClass));
    String content = ExecUtil.loadTemplate(CreateDesktopEntryAction.class.getClassLoader(), "entry.desktop", vars);
    File entryFile = new File(FileUtil.getTempDirectory(), wmClass + ".desktop");
    FileUtil.writeToFile(entryFile, content);
    return entryFile;
  }

  private static void install(File entryFile, boolean globalEntry) throws IOException, ExecutionException {
    if (globalEntry) {
      File script = ExecUtil.createTempExecutableScript(
        "create_desktop_entry_", ".sh",
        "#!/bin/sh\n" +
        "xdg-desktop-menu install --mode system '" + entryFile.getAbsolutePath() + "' && xdg-desktop-menu forceupdate --mode system\n");
      try {
        exec(new GeneralCommandLine(script.getPath()), ApplicationBundle.message("desktop.entry.sudo.prompt"));
      }
      finally {
        FileUtil.delete(script);
      }
    }
    else {
      exec(new GeneralCommandLine("xdg-desktop-menu", "install", "--mode", "user", entryFile.getAbsolutePath()), null);
      exec(new GeneralCommandLine("xdg-desktop-menu", "forceupdate", "--mode", "user"), null);
    }
  }

  private static void exec(GeneralCommandLine command, @Nullable String prompt) throws IOException, ExecutionException {
    command.setRedirectErrorStream(true);
    ProcessOutput result = prompt != null ? ExecUtil.sudoAndGetOutput(command, prompt) : ExecUtil.execAndGetOutput(command);
    int exitCode = result.getExitCode();
    if (exitCode != 0) {
      String message = "Command '" + (prompt != null ? "sudo " : "") + command.getCommandLineString() + "' returned " + exitCode;
      String output = result.getStdout();
      if (!StringUtil.isEmptyOrSpaces(output)) message += "\nOutput: " + output.trim();
      throw new RuntimeException(message);
    }
  }

  public static class CreateDesktopEntryDialog extends DialogWrapper {
    private JPanel myContentPane;
    private JLabel myLabel;
    private JCheckBox myGlobalEntryCheckBox;

    public CreateDesktopEntryDialog(final Project project) {
      super(project);
      init();
      setTitle(ApplicationBundle.message("desktop.entry.title"));
      myLabel.setText(myLabel.getText().replace("$APP_NAME$", ApplicationNamesInfo.getInstance().getProductName()));
    }

    @Override
    protected JComponent createCenterPanel() {
      return myContentPane;
    }
  }

  @Nullable
  public static String getLauncherScript() {
    return ourScript.getValue();
  }
}