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
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShowFilePathAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ShowFilePathAction");

  public static final NotificationListener FILE_SELECTING_LISTENER = new NotificationListener.Adapter() {
    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      URL url = e.getURL();
      if (url != null) openFile(new File(url.getPath()));
      notification.expire();
    }
  };

  private static NotNullLazyValue<Boolean> canUseNautilus = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      if (!SystemInfo.isUnix || !SystemInfo.hasXdgMime() || !new File("/usr/bin/nautilus").canExecute()) {
        return false;
      }

      String appName = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-mime", "query", "default", "inode/directory"));
      if (appName == null || !appName.matches("nautilus.*\\.desktop")) return false;

      String version = ExecUtil.execAndReadLine(new GeneralCommandLine("nautilus", "--version"));
      if (version == null) return false;

      Matcher m = Pattern.compile("GNOME nautilus ([0-9.]+)").matcher(version);
      return m.find() && StringUtil.compareVersionNumbers(m.group(1), "3") >= 0;
    }
  };

  private static final NotNullLazyValue<String> fileManagerName = new AtomicNotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      if (SystemInfo.isMac) return "Finder";
      if (SystemInfo.isWindows) return "Explorer";
      if (SystemInfo.isUnix && SystemInfo.hasXdgMime()) {
        String name = getUnixFileManagerName();
        if (name != null) return name;
      }
      return "File Manager";
    }
  };

  @Nullable
  private static String getUnixFileManagerName() {
    String appName = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-mime", "query", "default", "inode/directory"));
    if (appName == null || !appName.matches(".+\\.desktop")) return null;

    String dirs = System.getenv("XDG_DATA_DIRS");
    if (dirs == null) return null;

    try {
      for (String dir : dirs.split(File.pathSeparator)) {
        File appFile = new File(dir, "applications/" + appName);
        if (appFile.exists()) {
          BufferedReader reader = new BufferedReader(new FileReader(appFile));
          try {
            String line;
            while ((line = reader.readLine()) != null) {
              if (line.startsWith("Name=")) {
                return line.substring(5);
              }
            }
          }
          finally {
            reader.close();
          }
        }
      }
    }
    catch (IOException e) {
      LOG.info("Cannot read desktop file", e);
    }

    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean visible = !SystemInfo.isMac && isSupported();
    e.getPresentation().setVisible(visible);
    if (visible) {
      VirtualFile file = getFile(e);
      e.getPresentation().setEnabled(file != null);
      e.getPresentation().setText(ActionsBundle.message("action.ShowFilePath.tuned", file != null && file.isDirectory() ? 1 : 0));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = getFile(e);
    if (file != null) {
      show(file, popup -> {
        DataManager dataManager = DataManager.getInstance();
        if (dataManager != null) {
          dataManager.getDataContextFromFocus().doWhenDone(((Consumer<DataContext>)popup::showInBestPositionFor));
        }
      });
    }
  }

  public static void show(@NotNull VirtualFile file, @NotNull MouseEvent e) {
    show(file, popup -> {
      if (e.getComponent().isShowing()) {
        popup.show(new RelativePoint(e));
      }
    });
  }

  private static void show(@NotNull VirtualFile file, @NotNull ShowAction show) {
    if (!isSupported()) return;

    List<VirtualFile> files = new ArrayList<>();
    List<String> fileUrls = new ArrayList<>();
    VirtualFile eachParent = file;
    while (eachParent != null) {
      int index = files.size() == 0 ? 0 : files.size();
      files.add(index, eachParent);
      fileUrls.add(index, getPresentableUrl(eachParent));
      if (eachParent.getParent() == null && eachParent.getFileSystem() instanceof JarFileSystem) {
        eachParent = JarFileSystem.getInstance().getVirtualFileForJar(eachParent);
        if (eachParent == null) break;
      }
      eachParent = eachParent.getParent();
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<Icon> icons = new ArrayList<>();
      for (String url : fileUrls) {
        File ioFile = new File(url);
        icons.add(ioFile.exists() ? FileSystemView.getFileSystemView().getSystemIcon(ioFile) : EmptyIcon.ICON_16);
      }
      ApplicationManager.getApplication().invokeLater(() -> show.show(createPopup(files, icons)));
    });
  }

  private static String getPresentableUrl(VirtualFile file) {
    String url = file.getPresentableUrl();
    if (file.getParent() == null && SystemInfo.isWindows) url += "\\";
    return url;
  }

  private interface ShowAction {
    void show(ListPopup popup);
  }

  private static ListPopup createPopup(List<VirtualFile> files, List<Icon> icons) {
    BaseListPopupStep<VirtualFile> step = new BaseListPopupStep<VirtualFile>(RevealFileAction.getActionName(), files, icons) {
      @NotNull
      @Override
      public String getTextFor(final VirtualFile value) {
        return value.getPresentableName();
      }

      @Override
      public PopupStep onChosen(final VirtualFile selectedValue, final boolean finalChoice) {
        final File selectedFile = new File(getPresentableUrl(selectedValue));
        if (selectedFile.exists()) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> openFile(selectedFile));
        }
        return FINAL_CHOICE;
      }
    };

    return JBPopupFactory.getInstance().createListPopup(step);
  }

  public static boolean isSupported() {
    return SystemInfo.isWindows ||
           SystemInfo.hasXdgOpen() || canUseNautilus.getValue() ||
           Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
  }

  @NotNull
  public static String getFileManagerName() {
    return fileManagerName.getValue();
  }

  /**
   * Shows system file manager with given file's parent directory open and the file highlighted in it<br/>
   * (note that not all platforms support highlighting).
   *
   * @param file a file or directory to show and highlight in a file manager.
   */
  public static void openFile(@NotNull File file) {
    if (!file.exists()) return;
    file = file.getAbsoluteFile();
    File parent = file.getParentFile();
    if (parent == null) return;
    try {
      doOpen(parent, file);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  /**
   * Shows system file manager with given directory open in it.
   *
   * @param directory a directory to show in a file manager.
   */
  @SuppressWarnings("UnusedDeclaration")
  public static void openDirectory(@NotNull final File directory) {
    if (!directory.isDirectory()) return;
    try {
      doOpen(directory, null);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  private static void doOpen(@NotNull File dir, @Nullable File toSelect) throws IOException, ExecutionException {
    dir = new File(FileUtil.toCanonicalPath(dir.getPath()));
    toSelect = toSelect == null ? null : new File(FileUtil.toCanonicalPath(toSelect.getPath()));
    
    if (SystemInfo.isWindows) {
      String cmd;
      if (toSelect != null) {
        cmd = "explorer /select," + toSelect.getAbsolutePath();
      }
      else {
        cmd = "explorer /root," + dir.getAbsolutePath();
      }
      // no quoting/escaping is needed
      Runtime.getRuntime().exec(cmd);
      return;
    }

    if (SystemInfo.isMac) {
      if (toSelect != null) {
        final String script = String.format(
          "tell application \"Finder\"\n" +
          "\treveal {\"%s\"} as POSIX file\n" +
          "\tactivate\n" +
          "end tell", toSelect.getAbsolutePath());
        new GeneralCommandLine(ExecUtil.getOsascriptPath(), "-e", script).createProcess();
      }
      else {
        new GeneralCommandLine("open", dir.getAbsolutePath()).createProcess();
      }
      return;
    }

    if (canUseNautilus.getValue()) {
      new GeneralCommandLine("nautilus", (toSelect != null ? toSelect : dir).getAbsolutePath()).createProcess();
      return;
    }

    String path = dir.getAbsolutePath();
    if (SystemInfo.hasXdgOpen()) {
      new GeneralCommandLine("/usr/bin/xdg-open", path).createProcess();
    }
    else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      Desktop.getDesktop().open(new File(path));
    }
    else {
      Messages.showErrorDialog("This action isn't supported on the current platform", "Cannot Open File");
    }
  }

  @Nullable
  private static VirtualFile getFile(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    return files == null || files.length == 1 ? CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext()) : null;
  }

  public static Boolean showDialog(Project project, String message, String title, File file) {
    final Boolean[] ref = new Boolean[1];
    final DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return true;
      }

      @Override
      public void setToBeShown(boolean value, int exitCode) {
        if (!value) {
          if (exitCode == 0) {
            // yes
            ref[0] = true;
          }
          else {
            ref[0] = false;
          }
        }
      }

      @Override
      public boolean canBeHidden() {
        return true;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return true;
      }

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return CommonBundle.message("dialog.options.do.not.ask");
      }
    };
    showDialog(project, message, title, file, option);
    return ref[0];
  }

  public static void showDialog(Project project, String message, String title, File file, DialogWrapper.DoNotAskOption option) {
    if (Messages.showOkCancelDialog(project, message, title, RevealFileAction.getActionName(),
                                    IdeBundle.message("action.close"), Messages.getInformationIcon(), option) == Messages.OK) {
      openFile(file);
    }
  }

  @Nullable
  public static VirtualFile findLocalFile(@Nullable VirtualFile file) {
    if (file == null) return null;

    if (file.isInLocalFileSystem()) {
      return file;
    }

    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof JarFileSystem && file.getParent() == null) {
      return  ((JarFileSystem)fs).getLocalVirtualFileFor(file);
    }

    return null;
  }
}