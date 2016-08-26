/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.jna.platform.mac.XAttrUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Files downloaded from Internet are marked as 'quarantined' by OS X.
 * For such files opening urls of type file://path#fragment via
 * <a href="https://developer.apple.com/library/mac/documentation/Carbon/Conceptual/LaunchServicesConcepts/LSCIntro/LSCIntro.html">
 *   Launch Services API
 * </a>
 * (used internally by {@link java.awt.Desktop#browse(URI)}) won't work as expected (fragment will be ignored on file opening).
 * This class allows to clear quarantine status from folder containing Javadoc, if confirmed by user.
 */
public class JavadocQuarantineStatusCleaner {
  private static final Logger LOG = Logger.getInstance(JavadocQuarantineStatusCleaner.class);

  private static final String QUARANTINE_ATTRIBUTE = "com.apple.quarantine";

  public static void cleanIfNeeded(@NotNull VirtualFile javadocFolder) {
    Application application = ApplicationManager.getApplication();
    assert !application.isDispatchThread();
    if (!SystemInfo.isMac || !javadocFolder.isInLocalFileSystem() || !javadocFolder.isDirectory()) return;
    String folderPath = VfsUtilCore.virtualToIoFile(javadocFolder).getAbsolutePath();
    // UserDefinedFileAttributeView isn't supported by JDK for HFS+ extended attributes on OS X, so we resort to JNA
    if (XAttrUtil.getXAttr(folderPath, QUARANTINE_ATTRIBUTE) == null) return;
    application.invokeLater(() -> {
      int result = Messages.showYesNoDialog(ApplicationBundle.message("quarantine.dialog.message"),
                                            ApplicationBundle.message("quarantine.dialog.title"),
                                            null);
      if (result == Messages.YES) {
        cleanQuarantineStatusInBackground(folderPath);
      }
    }, ModalityState.any());
  }

  private static void cleanQuarantineStatusInBackground(@NotNull String folderPath) {
    ProgressIndicatorBase progressIndicator = new ProgressIndicatorBase();
    String message = ApplicationBundle.message("quarantine.clean.progress", folderPath);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(new Task.Backgroundable(null, message) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try(Stream<Path> s = Files.walk(Paths.get(folderPath))) {
          s.forEach(p -> {
            ProgressManager.checkCanceled();
            XAttrUtil.removeXAttr(p.toFile().getAbsolutePath(), QUARANTINE_ATTRIBUTE);
          });
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void onError(@NotNull Exception error) {
        LOG.warn(error);
        new Notification(ApplicationBundle.message("quarantine.error.group"),
                         ApplicationBundle.message("quarantine.error.title"),
                         ApplicationBundle.message("quarantine.error.message"),
                         NotificationType.WARNING).notify(null);
      }
    }, progressIndicator);
  }
}
