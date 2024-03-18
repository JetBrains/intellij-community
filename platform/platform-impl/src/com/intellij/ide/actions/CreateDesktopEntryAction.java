// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.AppUIUtilKt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Restarter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class CreateDesktopEntryAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreateDesktopEntryAction.class);

  public static boolean isAvailable() {
    return SystemInfo.isUnix && !SystemInfo.isMac && !ExternalUpdateManager.isCreatingDesktopEntries() && SystemInfo.hasXdgOpen();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = isAvailable();
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
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

    Path entry = null;
    try {
      check();
      entry = prepare();
      install(entry, globalEntry);
    }
    finally {
      if (entry != null) {
        Files.delete(entry);
      }
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
    String binPath = PathManager.getBinPath();
    assert new File(binPath).isDirectory() : "Invalid bin path: '" + binPath + "'";

    String iconPath = AppUIUtilKt.findAppIcon();
    if (iconPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.icon.missing", binPath));
    }

    Path starter = Restarter.getIdeStarter();
    if (starter == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.script.missing", binPath));
    }
    String execPath = StringUtil.wrapWithDoubleQuote(starter.toString());

    ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();

    String name = names.getFullProductNameWithEdition();
    String comment = StringUtil.notNullize(names.getMotto(), name);
    String wmClass = AppUIUtil.INSTANCE.getFrameClass();
    Map<String, String> vars = Map.of("$NAME$", name, "$SCRIPT$", execPath, "$ICON$", iconPath, "$COMMENT$", comment, "$WM_CLASS$", wmClass);
    String content = ExecUtil.loadTemplate(CreateDesktopEntryAction.class.getClassLoader(), "entry.desktop", vars);
    Path entryFile = Path.of(PathManager.getTempPath(), getDesktopEntryName());
    Files.writeString(entryFile, content);
    return entryFile;
  }

  private static void install(Path entryFile, boolean globalEntry) throws IOException, ExecutionException {
    if (globalEntry) {
      File script = ExecUtil.createTempExecutableScript(
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
    command.setRedirectErrorStream(true);
    ProcessOutput result = new CapturingProcessHandler(prompt != null ? ExecUtil.sudoCommand(command, prompt) : command).runProcess();
    int exitCode = result.getExitCode();
    if (exitCode != 0) {
      String message = "Command '" + (prompt != null ? "sudo " : "") + command.getCommandLineString() + "' returned " + exitCode;
      String output = result.getStdout();
      if (!output.isBlank()) message += "\nOutput: " + output.trim();
      throw new RuntimeException(message);
    }
  }

  public static final class CreateDesktopEntryDialog extends DialogWrapper {
    private static final @NlsSafe String APP_NAME_PLACEHOLDER = "$APP_NAME$";

    private JPanel myContentPane;
    private JLabel myLabel;
    private JCheckBox myGlobalEntryCheckBox;

    public CreateDesktopEntryDialog(final Project project) {
      super(project);
      init();
      setTitle(ApplicationBundle.message("desktop.entry.title"));
      myLabel.setText(myLabel.getText().replace(APP_NAME_PLACEHOLDER, ApplicationNamesInfo.getInstance().getProductName()));
    }

    @Override
    protected JComponent createCenterPanel() {
      return myContentPane;
    }
  }
}
