// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The class contains utilities for interacting with Linux AppArmor for solving the restricted user namespaces problem.
 * <a href="https://youtrack.jetbrains.com/articles/JBR-A-11">The problem description.</a>
 */
@ApiStatus.Experimental
public final class JBCefAppArmorUtils {
  private static final Logger LOG = Logger.getInstance(JBCefAppArmorUtils.class);
  private static final LazyInitializer.LazyValue<Boolean> myUnprivilegedUserNameSpacesRestricted = LazyInitializer.create(
    () -> areUnprivilegedUserNameSpacesRestrictedImpl());

  /**
   * Checks if unprivileged user namespaces are restricted.
   * This function call might be blocking.
   * The function runs `unshare` command and tries to create a new user namespace.
   * See <a href="https://man7.org/linux/man-pages/man1/unshare.1.html">man page</a>
   */
  public static boolean areUnprivilegedUserNamespacesRestricted() {
    return myUnprivilegedUserNameSpacesRestricted.get();
  }

  public static @NotNull InlineBanner createUnprivilegedUserNamespacesRestrictedBanner() {
    String message = "%s. %s".formatted(IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.title"),
                                        IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.message"));
    return
      new InlineBanner(message, EditorNotificationPanel.Status.Error)
        .setMessage(message)
        .showCloseButton(false)
        .addAction(IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.add.apparmor.profile"),
                   () -> {
                     installAppArmorProfile();
                   })
        .addAction(IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.disable.sandbox"),
                   () -> {
                     RegistryManager.getInstance().get("ide.browser.jcef.sandbox.enable").setValue(false);
                     ApplicationManager.getApplication().restart();
                   })
        .addAction(IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.learn.more"),
                   () -> {
                     BrowserUtil.browse("https://youtrack.jetbrains.com/articles/JBR-A-11");
                   });
  }

  public static void showUnprivilegedUserNamespacesRestrictedDialog(Component parentComponent) {
    UIUtil.invokeLaterIfNeeded(() -> {
      int chose =
        Messages.showDialog(parentComponent,
                            IdeBundle.message("notification.content.jcef.enable.browser.dialog.message"),
                            IdeBundle.message("notification.content.jcef.enable.browser.dialog.title"),
                            new String[]{
                              IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.add.apparmor.profile"),
                              IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.disable.sandbox"),
                              CommonBundle.getCancelButtonText()
                            },
                            0, // default option
                            Messages.getQuestionIcon());

      switch (chose) {
        case 0:
          installAppArmorProfile();
          break;
        case 1:
          RegistryManager.getInstance().get("ide.browser.jcef.sandbox.enable").setValue(false);
          ApplicationManager.getApplication().restart();
          break;
      }
    });
  }

  public static JPanel getUnprivilegedUserNamespacesRestrictedStubPanel() {
    JPanel stubPanel = new JPanel(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.gridwidth = GridBagConstraints.REMAINDER;

    JLabel label = new JLabel(IdeBundle.message("notification.content.jcef.browser.suspended.text"));
    JButton button = new JButton(IdeBundle.message("notification.content.jcef.enable.browser.button"));
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showUnprivilegedUserNamespacesRestrictedDialog(stubPanel);
      }
    });

    stubPanel.add(label, gbc);
    stubPanel.add(button, gbc);

    return stubPanel;
  }

  /**
   * @deprecated The name of the function is not accurate.
   * Use {@link JBCefAppArmorUtils#areUnprivilegedUserNamespacesRestricted()}
   * This function is here yet just to simplify cherry-picking.
   * To be removed by another commit.
   */
  @Deprecated(forRemoval = true)
  static boolean areUnprivilegedUserNameSpacesAllowed() {
    return !myUnprivilegedUserNameSpacesRestricted.get();
  }

  private static boolean areUnprivilegedUserNameSpacesRestrictedImpl() {
    if (!SystemInfoRt.isLinux) {
      return false;
    }

    GeneralCommandLine cl = new GeneralCommandLine()
      .withExePath("unshare")
      .withParameters("--user", "--map-root-user", "echo");

    try {
      CapturingProcessHandler handler = new CapturingProcessHandler(cl);
      ProcessOutput output = handler.runProcess();
      if (output.getExitCode() == 0) {
        return false;
      }

      LOG.warn("Unprivileged user namespaces check failed: " + output.getStderr());
      return true;
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to check unprivileged user namespaces restrictions(considered as restricted): " + e.getMessage());
      return true;
    }
  }

  private static String getApparmorProfile() {
    String executablePath = ProcessHandle.current().info().command().orElse(null);
    if (executablePath == null) {
      LOG.warn("Can't generate the apparmor profile for JCEF: failed to find the executable path");
      return null;
    }

    return """
      # This profile is autogenerated by %s to allow running sandboxed JCEF
      abi <abi/4.0>,
      include <tunables/global>
      
      profile %s flags=(unconfined) {
        userns,
      
        include if exists <local/chrome>
      }
      """.formatted(ApplicationNamesInfo.getInstance().getFullProductNameWithEdition(), executablePath).stripIndent();
  }

  private static String getApplicationName() {
    return (ApplicationNamesInfo.getInstance().getProductName() + "-" + ApplicationNamesInfo.getInstance().getEditionName())
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]", "-");
  }

  private static String getApparmorProfilePath() {
    final Path configDirPath = Path.of("/etc/apparmor.d");
    if (!Files.exists(configDirPath) || !Files.isDirectory(configDirPath)) {
      LOG.warn("Can't generate the apparmor profile for CEF: /etc/apparmor.d doesn't exists");
      return null;
    }

    String appName = getApplicationName();
    for (int i = 0; i <= 1000; ++i) {
      String fileName = appName + (i == 0 ? "" : "-" + i);
      Path configPath = configDirPath.resolve(fileName);
      if (!Files.exists(configPath)) {
        return configPath.toString();
      }
    }

    LOG.warn("Can't generate the apparmor profile for CEF: failed to find the filename");
    return null;
  }

  private static void installAppArmorProfile() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String installationPath = getApparmorProfilePath();
      String profileText = getApparmorProfile();
      try {
        installAppArmorProfile(installationPath, profileText);
      }
      catch (IOException | ExecutionException ex) {
        Notification notification = JBCefApp.getNotificationGroup().createNotification(
          IdeBundle.message("notification.content.jcef.failed.to.install.apparmor.profile"), ex.getMessage(), NotificationType.ERROR);
        Notifications.Bus.notify(notification);
        return;
      }
      ApplicationManager.getApplication().restart();
    });
  }

  private static void installAppArmorProfile(String path, String content) throws IOException, ExecutionException {
    File tmpPrifile = FileUtil.createTempFile("apparmor_profile", null, true);
    FileUtil.writeToFile(tmpPrifile, content);

    File installScript = FileUtil.createTempFile("install_apparmor_profile.sh", null, true);
    FileUtil.writeToFile(installScript, """
      #!/bin/sh
      set -e
      cp %s %s
      apparmor_parser -r %s
      """.formatted(tmpPrifile, path, path).stripIndent());

    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withExePath("sh")
      .withParameters(installScript.toString());

    commandLine = ExecUtil.sudoCommand(
      commandLine,
      IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.install.apparmor.profile.prompt")
        .formatted(ApplicationNamesInfo.getInstance().getFullProductNameWithEdition())
    );

    ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
    if (output.getExitCode() != 0) {
      throw new ExecutionException(output.getStderr());
    }
  }

  static AnAction getInstallInstallAppArmorProfileAction(Runnable onComplete) {
    String installationPath = getApparmorProfilePath();
    String profileText = getApparmorProfile();
    if (installationPath == null || profileText == null) {
      return null;
    }

    return new InstallAppArmorProfileAction(installationPath, profileText, onComplete);
  }

  private static class InstallAppArmorProfileAction extends DumbAwareAction {
    private final String path;
    private final String profileContent;
    private final Runnable onComplete;

    InstallAppArmorProfileAction(String path, String content, Runnable onComplete) {
      super(IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.add.apparmor.profile"));

      this.path = path;
      this.profileContent = content;
      this.onComplete = onComplete;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          installAppArmorProfile(path, profileContent);
        }
        catch (IOException | ExecutionException ex) {
          Notification notification = JBCefApp.getNotificationGroup().createNotification(
            IdeBundle.message("notification.content.jcef.failed.to.install.apparmor.profile"), ex.getMessage(), NotificationType.ERROR);
          Notifications.Bus.notify(notification);
          return;
        }
        onComplete.run();
        ApplicationManager.getApplication().restart();
      });
    }
  }
}
