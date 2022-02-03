// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.JavaUiBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.jna.platform.mac.XAttrUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Files downloaded from Internet are marked as 'quarantined' by OS X.
 * For such files, opening URLs of type file://path#fragment via
 * <a href="https://developer.apple.com/library/mac/documentation/Carbon/Conceptual/LaunchServicesConcepts/LSCIntro/LSCIntro.html">
 *   Launch Services API
 * </a>
 * (used internally by {@link java.awt.Desktop#browse(URI)}) won't work as expected (fragment will be ignored on file opening).
 * This class allows to clear quarantine status from folder containing Javadoc, if confirmed by user.
 *
 * Implementation note: UserDefinedFileAttributeView is not supported on macOS (https://bugs.openjdk.java.net/browse/JDK-8030048),
 * so the class resorts to JNA.
 */
public final class JavadocQuarantineStatusCleaner {
  private static final Logger LOG = Logger.getInstance(JavadocQuarantineStatusCleaner.class);

  private static final String QUARANTINE_ATTRIBUTE = "com.apple.quarantine";

  public static void cleanIfNeeded(VirtualFile @NotNull ... docFolders) {
    if (docFolders.length > 0 && SystemInfo.isMac) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<@NlsSafe String> quarantined = Stream.of(docFolders)
          .filter(f -> f.isInLocalFileSystem() && f.isDirectory() && XAttrUtil.getXAttr(f.getPath(), QUARANTINE_ATTRIBUTE) != null)
          .map(VirtualFile::getPath)
          .collect(Collectors.toList());
        if (!quarantined.isEmpty()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            String title = JavaUiBundle.message("quarantine.cleaner");
            String message = JavaUiBundle.message("quarantine.dialog.message", StringUtil.join(quarantined, "\n"));
            if (MessageDialogBuilder.yesNo(title, message).show() == Messages.YES) {
              cleanQuarantineStatusInBackground(quarantined);
            }
          }, ModalityState.any());
        }
      });
    }
  }

  private static void cleanQuarantineStatusInBackground(List<@NlsSafe String> paths) {
    new Task.Backgroundable(null, JavaUiBundle.message("quarantine.clean.progress"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (@NlsSafe String path : paths) {
          indicator.checkCanceled();
          indicator.setText2(path);
          try (Stream<Path> s = Files.walk(Paths.get(path))) {
            s.forEach(p -> {
              indicator.checkCanceled();
              XAttrUtil.removeXAttr(p.toFile().getAbsolutePath(), QUARANTINE_ATTRIBUTE);
            });
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        LOG.warn(error);
        String title = JavaUiBundle.message("quarantine.cleaner");
        String message = JavaUiBundle.message("quarantine.error.message", error.getMessage());
        NotificationGroupManager.getInstance().getNotificationGroup("Quarantine Cleaner").createNotification(title, message, NotificationType.WARNING)
          .notify(null);
      }
    }.queue();
  }
}