// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.SystemProperties;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.intellij.openapi.util.text.StringUtil.defaultIfEmpty;

/**
 * This helpful action opens a file or directory in a system file manager.
 *
 * @see ShowFilePathAction
 */
public class RevealFileAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(RevealFileAction.class);

  public static final NotificationListener FILE_SELECTING_LISTENER = new NotificationListener.Adapter() {
    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      URL url = e.getURL();
      if (url != null) {
        try {
          openFile(new File(url.toURI()));
        }
        catch (URISyntaxException ex) {
          LOG.warn("invalid URL: " + url, ex);
        }
      }
      notification.expire();
    }
  };

  public RevealFileAction() {
    getTemplatePresentation().setText(getActionName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isSupported() && getFile(e) != null);
    e.getPresentation().setText(getActionName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = getFile(e);
    if (file != null) {
      openFile(new File(file.getPresentableUrl()));
    }
  }

  @Nullable
  private static VirtualFile getFile(@NotNull AnActionEvent e) {
    return findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
  }

  public static boolean isSupported() {
    return SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.hasXdgOpen() ||
           Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
  }

  @NotNull
  public static String getActionName() {
    return SystemInfo.isMac ? ActionsBundle.message("action.RevealIn.name.mac") : ActionsBundle.message("action.RevealIn.name.other", getFileManagerName());
  }

  @NotNull
  public static String getFileManagerName() {
    return fileManagerName.getValue();
  }

  @Nullable
  public static VirtualFile findLocalFile(@Nullable VirtualFile file) {
    if (file == null || file.isInLocalFileSystem()) {
      return file;
    }

    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof ArchiveFileSystem && file.getParent() == null) {
      return ((ArchiveFileSystem)fs).getLocalByEntry(file);
    }

    return null;
  }

  public static void showDialog(Project project, String message, String title, @NotNull File file, @Nullable DialogWrapper.DoNotAskOption option) {
    String ok = getActionName();
    String cancel = IdeBundle.message("action.close");
    if (Messages.showOkCancelDialog(project, message, title, ok, cancel, Messages.getInformationIcon(), option) == Messages.OK) {
      openFile(file);
    }
  }

  /**
   * Opens a system file manager with given file's parent directory open and the file highlighted in it
   * (note that not all platforms support highlighting).
   */
  public static void openFile(@NotNull File file) {
    if (!file.exists()) {
      LOG.info("does not exist: " + file);
      return;
    }

    File parent = file.getAbsoluteFile().getParentFile();
    if (parent != null) {
      doOpen(parent, file);
    }
    else {
      doOpen(file, null);
    }
  }

  /**
   * Opens a system file manager with given directory open in it.
   */
  public static void openDirectory(@NotNull File directory) {
    if (!directory.isDirectory()) {
      LOG.info("not a directory: " + directory);
      return;
    }

    doOpen(directory.getAbsoluteFile(), null);
  }

  private static void doOpen(@NotNull File _dir, @Nullable File _toSelect) {
    String dir = FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(_dir.getPath()));
    String toSelect = _toSelect != null ? FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(_toSelect.getPath())) : null;
    String fmApp;

    if (SystemInfo.isWindows) {
      spawn(toSelect != null ? "explorer /select,\"" + shortPath(toSelect) + '"' : "explorer /root,\"" + shortPath(dir) + '"');
    }
    else if (SystemInfo.isMac) {
      if (toSelect != null) {
        spawn("open", "-R", toSelect);
      }
      else {
        spawn("open", dir);
      }
    }
    else if ((fmApp = fileManagerApp.getValue()) != null) {
      if (fmApp.endsWith("dolphin") && toSelect != null) {
        spawn(fmApp, "--select", toSelect);
      }
      else {
        spawn(fmApp, toSelect != null ? toSelect : dir);
      }
    }
    else if (SystemInfo.hasXdgOpen()) {
      spawn("xdg-open", dir);
    }
    else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      LOG.debug("opening " + dir + " via Desktop API");
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          Desktop.getDesktop().open(new File(dir));
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      });
    }
    else {
      Messages.showErrorDialog("This action isn't supported on the current platform", "Cannot Open File");
    }
  }

  private static String shortPath(String path) {
    if (path.contains("  ") && JnaLoader.isLoaded()) {
      // On the way from Runtime.exec() to CreateProcess(), a command line goes through couple rounds of merging and splitting
      // which breaks paths containing a sequence of two or more spaces.
      // Conversion to a short format is an ugly hack allowing to open such paths in Explorer.
      char[] result = new char[WinDef.MAX_PATH];
      if (Kernel32.INSTANCE.GetShortPathName(path, result, result.length) <= result.length) {
        return Native.toString(result);
      }
    }

    return path;
  }

  private static void spawn(String... command) {
    LOG.debug(Arrays.toString(command));

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        CapturingProcessHandler handler;
        if (SystemInfo.isWindows) {
          assert command.length == 1;
          Process process = Runtime.getRuntime().exec(command[0]);  // no quoting/escaping is needed
          handler = new CapturingProcessHandler.Silent(process, null, command[0]);
        }
        else {
          handler = new CapturingProcessHandler.Silent(new GeneralCommandLine(command));
        }
        handler.runProcess(10000, false).checkSuccess(LOG);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    });
  }

  private static final NullableLazyValue<String> fileManagerApp = new AtomicNullableLazyValue<String>() {
    @Override
    protected String compute() {
      return readDesktopEntryKey("Exec")
        .map(line -> line.split(" ")[0])
        .filter(exec -> exec.endsWith("nautilus") || exec.endsWith("pantheon-files") || exec.endsWith("dolphin"))
        .orElse(null);
    }
  };

  private static final NotNullLazyValue<String> fileManagerName = new AtomicNotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      if (SystemInfo.isMac) return "Finder";
      if (SystemInfo.isWindows) return "Explorer";
      return readDesktopEntryKey("Name").orElse("File Manager");
    }
  };

  private static Optional<String> readDesktopEntryKey(String key) {
    if (SystemInfo.hasXdgMime()) {
      String appName = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-mime", "query", "default", "inode/directory"));
      if (appName != null && appName.endsWith(".desktop")) {
        return Stream.of(getXdgDataDirectories().split(":"))
          .map(dir -> new File(dir, "applications/" + appName))
          .filter(File::exists)
          .findFirst()
          .map(file -> readDesktopEntryKey(file, key));
      }
    }

    return Optional.empty();
  }

  private static String getXdgDataDirectories() {
    String dataHome = System.getenv("XDG_DATA_HOME");
    String dataDirs = System.getenv("XDG_DATA_DIRS");
    return defaultIfEmpty(dataHome, SystemProperties.getUserHome() + "/.local/share") + ':' + defaultIfEmpty(dataDirs, "/usr/local/share:/usr/share");
  }

  private static String readDesktopEntryKey(File file, String key) {
    LOG.debug("looking for '" + key + "' in " + file);
    String prefix = key + '=';
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      return reader.lines().filter(l -> l.startsWith(prefix)).map(l -> l.substring(prefix.length())).findFirst().orElse(null);
    }
    catch (IOException | UncheckedIOException e) {
      LOG.info("Cannot read: " + file, e);
      return null;
    }
  }
}