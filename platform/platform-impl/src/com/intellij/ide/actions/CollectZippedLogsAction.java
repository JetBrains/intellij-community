// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.troubleshooting.TroubleInfoCollector;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

public class CollectZippedLogsAction extends AnAction implements DumbAware {

  private static final String CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog";
  private static class Holder {
    private static final NotificationGroup NOTIFICATION_GROUP =
      new NotificationGroup("Collect Zipped Logs", NotificationDisplayType.BALLOON, true);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();

    final boolean doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG);

    if (!doNotShowDialog) {
      int result = Messages.showOkCancelDialog(
        project, IdeBundle.message("message.included.logs.and.settings.may.contain.sensitive.data"),
        IdeBundle.message("dialog.title.sensitive.data"),
        "Show in " + RevealFileAction.getFileManagerName(), "Cancel", Messages.getWarningIcon(),
        new DialogWrapper.DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(final boolean selected, final int exitCode) {
            PropertiesComponent.getInstance().setValue(CONFIRMATION_DIALOG, selected);
          }
        }
      );
      if (result == Messages.CANCEL) return;
    }
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        final File zippedLogsFile = createZip(project, compressor -> {});
        if (RevealFileAction.isSupported()) {
          RevealFileAction.openFile(zippedLogsFile);
        }
        else {
          final Notification logNotification = new Notification(Holder.NOTIFICATION_GROUP.getDisplayId(),
                                                                "",
                                                                IdeBundle.message("notification.content.log.file.is.created.0",
                                                                                  zippedLogsFile.getAbsolutePath()),
                                                                NotificationType.INFORMATION);
          Notifications.Bus.notify(logNotification);
        }
      }
      catch (final IOException exception) {
        Logger.getInstance(getClass()).warn(exception);

        final Notification errorNotification = new Notification(Holder.NOTIFICATION_GROUP.getDisplayId(),
                                                                "",
                                                                IdeBundle.message("notification.content.can.t.create.zip.file.with.logs.0",
                                                                                  exception.getLocalizedMessage()),
                                                                NotificationType.ERROR);
        Notifications.Bus.notify(errorNotification);
      }
    }, IdeBundle.message("progress.title.collecting.logs"), false, project);
  }

  @NotNull
  @ApiStatus.Internal
  public static File createZip(@Nullable Project project,
                               @NotNull Consumer<@NotNull Compressor> additionalFiles) throws IOException {
    PerformanceWatcher.getInstance().dumpThreads("", false);

    String productName = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getLowercaseProductName());
    File zippedLogsFile = FileUtil.createTempFile(productName + "-logs-" + getDate(), ".zip");

    try (Compressor zip = new Compressor.Zip(zippedLogsFile)) {
      // Add additional files before logs folder to collect any logging
      // happened in additionalFiles.accept
      additionalFiles.accept(zip);

      zip.addDirectory(new File(PathManager.getLogPath()));

      StringBuilder troubleshooting = collectInfoFromExtensions(project);
      if (troubleshooting != null) {
        zip.addFile("troubleshooting.txt", troubleshooting.toString().getBytes(StandardCharsets.UTF_8));
      }

      for (File javaErrorLog : getJavaErrorLogs()) {
        zip.addFile(javaErrorLog.getName(), javaErrorLog);
      }
    }
    catch (IOException exception) {
      FileUtil.delete(zippedLogsFile);
      throw exception;
    }

    return zippedLogsFile;
  }

  @Nullable
  private static StringBuilder collectInfoFromExtensions(@Nullable Project project) {
    StringBuilder settings = null;
    if (project != null) {
      settings = new StringBuilder();
      settings.append(new CompositeGeneralTroubleInfoCollector().collectInfo(project));
      for (TroubleInfoCollector troubleInfoCollector : TroubleInfoCollector.EP_SETTINGS.getExtensions()) {
        settings.append(troubleInfoCollector.collectInfo(project)).append('\n');
      }
    }
    return settings;
  }

  private static File[] getJavaErrorLogs() {
    return new File(SystemProperties.getUserHome())
      .listFiles(file -> file.isFile() && file.getName().startsWith("java_error_in") && !file.getName().endsWith("hprof"));
  }

  @NotNull
  private static String getDate() {
    return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
  }
}