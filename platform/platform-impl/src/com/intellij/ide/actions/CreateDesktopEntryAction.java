/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CreateDesktopEntryAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateDesktopEntryAction");

  private static final int MIN_ICON_SIZE = 32;

  @Override
  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(SystemInfo.isLinux);
    presentation.setVisible(SystemInfo.isLinux);
  }

  @Override
  public void actionPerformed(final AnActionEvent event) {
    if (!SystemInfo.isLinux) return;

    ProgressManager.getInstance().run(new Task.Backgroundable(null, event.getPresentation().getText()) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          indicator.setText(ApplicationBundle.message("desktop.entry.checking"));
          check();
          indicator.setFraction(0.33);

          indicator.setText(ApplicationBundle.message("desktop.entry.preparing"));
          final File entry = prepare();
          indicator.setFraction(0.66);

          indicator.setText(ApplicationBundle.message("desktop.entry.installing"));
          install(entry);
          indicator.setFraction(1.0);
    
          final String message = ApplicationBundle.message("desktop.entry.success",
                                                           ApplicationNamesInfo.getInstance().getProductName());
          Notifications.Bus.notify(
            new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Desktop entry created", message, NotificationType.INFORMATION)
          );
        }
        catch (Exception e) {
          final String message = e.getMessage();
          if (!StringUtil.isEmptyOrSpaces(message)) {
            LOG.warn(e);
            Notifications.Bus.notify(
              new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Unable to create desktop entry", message, NotificationType.ERROR)
            );
          }
          else {
            LOG.error(e);
          }
        }
      }
    });
  }

  private static void check() {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath("which");
    cmd.addParameter("xdg-desktop-menu");

    try {
      final Process process = cmd.createProcess();
      final int result = process.waitFor();
      if (result != 0) throw new Exception("'" + cmd.getCommandLineString() + "' : " + result);
    }
    catch (Exception e) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.xdg.missing"), e);
    }
  }

  private static File prepare() throws IOException {
    final String homePath = PathManager.getHomePath();
    assert homePath != null && new File(homePath).isDirectory() : "Invalid home path: '" + homePath + "'";
    final String binPath = homePath + "/bin";
    assert new File(binPath).isDirectory() : "Invalid bin/ path: '" + binPath + "'";

    String name = ApplicationNamesInfo.getInstance().getFullProductName();
    if (PlatformUtils.isCommunity()) name += " Community Edition";

    final String iconPath = findIcon(binPath);
    if (iconPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.icon.missing", binPath));
    }

    final String execPath = findScript(binPath);
    if (execPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.script.missing", binPath));
    }

    final String wmClass = AppUIUtil.getFrameClass();

    final InputStream stream = CreateDesktopEntryAction.class.getClassLoader().getResourceAsStream("entry.desktop");
    assert stream != null : CreateDesktopEntryAction.class.getClassLoader();
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    String content = FileUtil.loadTextAndClose(new InputStreamReader(stream));
    content = StringUtil.replace(content, "$NAME$", name);
    content = StringUtil.replace(content, "$SCRIPT$", execPath);
    content = StringUtil.replace(content, "$ICON$", iconPath);
    content = StringUtil.replace(content, "$WM_CLASS$", wmClass);

    final String entryName = wmClass + ".desktop";
    final File entryFile = new File(FileUtil.getTempDirectory(), entryName);
    FileUtil.writeToFile(entryFile, content);

    return entryFile;
  }

  // idea128.png, idea_CE128.png, flexide.png (32), RMlogo.svg, PyCharm_128.png, webide.png (128)
  @Nullable
  private static String findIcon(final String iconsPath) {
    final File iconsDir = new File(iconsPath);

    // 1. look for .svg icon
    for (String child : iconsDir.list()) {
      if (child.endsWith(".svg")) {
        return iconsPath + '/' + child;
      }
    }

    // 2. look for .png icon of max size
    int max = 0;
    String iconPath = null;
    for (String child : iconsDir.list()) {
      if (!child.endsWith(".png")) continue;
      final String path = iconsPath + '/' + child;
      final Icon icon = new ImageIcon(path);
      final int size = icon.getIconHeight();
      if (size >= MIN_ICON_SIZE && size > max && size == icon.getIconWidth()) {
        max = size;
        iconPath = path;
      }
    }

    return iconPath;
  }

  // idea.sh, flexide.sh, rubymine.sh, pycharm.sh, WebStorm.sh, PhpStorm.sh
  @Nullable
  private static String findScript(final String binPath) {
    final String productName = ApplicationNamesInfo.getInstance().getProductName();

    String execPath = binPath + '/' + productName + ".sh";
    if (new File(execPath).canExecute()) return execPath;

    execPath = binPath + '/' + productName.toLowerCase() + ".sh";
    if (new File(execPath).canExecute()) return execPath;

    return null;
  }

  private static void install(final File entryFile) throws ExecutionException, InterruptedException {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath("xdg-desktop-menu");
    cmd.addParameters("install", "--mode", "user", entryFile.getAbsolutePath());
    try {
      final Process process = cmd.createProcess();
      final int result = process.waitFor();
      if (result != 0) throw new RuntimeException("'" + cmd.getCommandLineString() + "' : " + result);
    }
    finally {
      if (!entryFile.delete()) LOG.error("Failed to delete temp file '" + entryFile + "'");
    }
  }
}
