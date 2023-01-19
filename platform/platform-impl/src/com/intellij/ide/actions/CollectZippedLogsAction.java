// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.troubleshooting.TroubleInfoCollector;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.io.Compressor;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

public class CollectZippedLogsAction extends AnAction implements DumbAware {
  private static final String CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog";
  public static final String NOTIFICATION_GROUP = "Collect Zipped Logs";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    boolean doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG);
    if (!doNotShowDialog) {
      String title = IdeBundle.message("collect.logs.sensitive.title");
      String message = IdeBundle.message("collect.logs.sensitive.text");
      boolean confirmed = MessageDialogBuilder.okCancel(title, message)
        .yesText(ActionsBundle.message("action.RevealIn.name.other", RevealFileAction.getFileManagerName()))
        .noText(CommonBundle.getCancelButtonText())
        .icon(Messages.getWarningIcon())
        .doNotAsk(new DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean selected, int exitCode) {
            PropertiesComponent.getInstance().setValue(CONFIRMATION_DIALOG, selected);
          }
        })
        .ask(project);
      if (!confirmed) {
        return;
      }
    }

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        Path logs = packLogs(project);
        if (RevealFileAction.isSupported()) {
          RevealFileAction.openFile(logs);
        }
        else {
          new Notification(NOTIFICATION_GROUP, IdeBundle.message("collect.logs.notification.success", logs), NotificationType.INFORMATION).notify(project);
        }
      }
      catch (IOException x) {
        Logger.getInstance(getClass()).warn(x);
        String message = IdeBundle.message("collect.logs.notification.error", IoErrorText.message(x));
        new Notification(NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(project);
      }
    }, IdeBundle.message("collect.logs.progress.title"), true, project);
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  public static @NotNull Path packLogs(@Nullable Project project) throws IOException {
    return packLogs(project, compressor -> { });
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  public static @NotNull Path packLogs(@Nullable Project project, @NotNull Consumer<? super Compressor> additionalFiles) throws IOException {
    PerformanceWatcher.getInstance().dumpThreads("", false);

    String productName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.ENGLISH);
    @SuppressWarnings("SpellCheckingInspection") String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    Path archive = Files.createTempFile(productName + "-logs-" + date, ".zip");

    try (Compressor zip = new Compressor.Zip(archive.toFile())) {
      // packing additional files before logs, to collect any problems happening in the process
      ProgressManager.checkCanceled();
      additionalFiles.accept(zip);

      ProgressManager.checkCanceled();
      Path logs = Path.of(PathManager.getLogPath()), caches = Path.of(PathManager.getSystemPath());
      if (Files.isSameFile(logs, caches)) {
        throw new IOException("cannot collect logs, because log directory set to be the same as the 'system' one: " + logs);
      }
      zip.addDirectory(logs);

      ProgressManager.checkCanceled();
      if (project != null) {
        StringBuilder settings = new StringBuilder();
        settings.append(new CompositeGeneralTroubleInfoCollector().collectInfo(project));
        for (TroubleInfoCollector troubleInfoCollector : TroubleInfoCollector.EP_SETTINGS.getExtensions()) {
          ProgressManager.checkCanceled();
          settings.append(troubleInfoCollector.collectInfo(project)).append('\n');
        }
        zip.addFile("troubleshooting.txt", settings.toString().getBytes(StandardCharsets.UTF_8));
      }

      // JVM crash logs
      try (DirectoryStream<Path> paths = Files.newDirectoryStream(Path.of(SystemProperties.getUserHome()))) {
        for (Path path : paths) {
          ProgressManager.checkCanceled();
          String name = path.getFileName().toString();
          if ((name.startsWith("java_error_in") || name.startsWith("jbr_err_pid")) && !name.endsWith("hprof") && Files.isRegularFile(path)) {
            zip.addFile(name, path);
          }
        }
      }
    }
    catch (IOException e) {
      try {
        Files.delete(archive);
      }
      catch (IOException x) {
        e.addSuppressed(x);
      }
      throw e;
    }

    return archive;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
