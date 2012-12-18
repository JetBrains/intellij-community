/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShowFilePathAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ShowFilePathAction");

  private static NotNullLazyValue<Boolean> hasNautilusV3 = new NotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      final String version = ExecUtil.execAndReadLine("nautilus", "--version");
      if (version == null) return false;
      final Matcher m = Pattern.compile("GNOME nautilus ([0-9.]+)").matcher(version);
      return m.find() && StringUtil.compareVersionNumbers(m.group(1), "3") >= 0;
    }
  };

  @Override
  public void update(final AnActionEvent e) {
    if (SystemInfo.isMac || !isSupported()) {
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setEnabled(getFile(e) != null);
  }

  public void actionPerformed(final AnActionEvent e) {
    show(getFile(e), new ShowAction() {
      public void show(final ListPopup popup) {
        final DataContext context = DataManager.getInstance().getDataContext();
        popup.showInBestPositionFor(context);
      }
    });
  }

  public static void show(final VirtualFile file, final MouseEvent e) {
    show(file, new ShowAction() {
      public void show(final ListPopup popup) {
        if (!e.getComponent().isShowing()) return;
        popup.show(new RelativePoint(e));
      }
    });
  }

  public static void show(final VirtualFile file, final ShowAction show) {
    if (!isSupported()) return;

    final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    final ArrayList<String> fileUrls = new ArrayList<String>();
    VirtualFile eachParent = file;
    while (eachParent != null) {
      final int index = files.size() == 0 ? 0 : files.size();
      files.add(index, eachParent);
      fileUrls.add(index, getPresentableUrl(eachParent));
      if (eachParent.getParent() == null && eachParent.getFileSystem() instanceof JarFileSystem) {
        eachParent = JarFileSystem.getInstance().getVirtualFileForJar(eachParent);
        if (eachParent == null) break;
      }
      eachParent = eachParent.getParent();
    }


    final ArrayList<Icon> icons = new ArrayList<Icon>();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        for (String each : fileUrls) {
          final File ioFile = new File(each);
          Icon eachIcon;
          if (ioFile.exists()) {
            eachIcon = FileSystemView.getFileSystemView().getSystemIcon(ioFile);
          }
          else {
            eachIcon = EmptyIcon.ICON_16;
          }

          icons.add(eachIcon);
        }

        LaterInvocator.invokeLater(new Runnable() {
          public void run() {
            show.show(createPopup(files, icons));
          }
        });
      }
    });
  }

  private static String getPresentableUrl(final VirtualFile eachParent) {
    String url = eachParent.getPresentableUrl();
    if (eachParent.getParent() == null && SystemInfo.isWindows) {
      url += "\\";
    }
    return url;
  }

  interface ShowAction {
    void show(ListPopup popup);
  }

  private static ListPopup createPopup(final ArrayList<VirtualFile> files, final ArrayList<Icon> icons) {
    final BaseListPopupStep<VirtualFile> step = new BaseListPopupStep<VirtualFile>("File Path", files, icons) {
      @NotNull
      @Override
      public String getTextFor(final VirtualFile value) {
        return value.getPresentableName();
      }

      @Override
      public PopupStep onChosen(final VirtualFile selectedValue, final boolean finalChoice) {
        final File selectedFile = new File(getPresentableUrl(selectedValue));
        if (selectedFile.exists()) {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
              openFile(selectedFile);
            }
          });
        }
        return FINAL_CHOICE;
      }
    };

    return JBPopupFactory.getInstance().createListPopup(step);
  }

  public static boolean isSupported() {
    return SystemInfo.isWindows ||
           Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) ||
           SystemInfo.hasXdgOpen() || SystemInfo.hasNautilus();
  }

  /** @deprecated use {@linkplain #openFile(java.io.File)} (to remove in IDEA 13) */
  public static void open(@NotNull final File ioFile, @Nullable final File toSelect) {
    openFile(toSelect != null && toSelect.exists() ? toSelect : ioFile);
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
    if (parent == null) {
      return;
    }
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
  public static void openDirectory(@NotNull final File directory) {
    if (!directory.isDirectory()) return;
    try {
      doOpen(directory, null);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  private static void doOpen(@NotNull final File dir, @Nullable final File toSelect) throws IOException, ExecutionException {
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

    if (Registry.is("ide.use.nautilus3") && SystemInfo.hasNautilus() && hasNautilusV3.getValue()) {
      if (toSelect != null) {
        new GeneralCommandLine("nautilus", toSelect.getAbsolutePath()).createProcess();
      }
      else {
        new GeneralCommandLine("nautilus", dir.getAbsolutePath()).createProcess();
      }
      return;
    }

    final String path = dir.getAbsolutePath();
    if (SystemInfo.hasXdgOpen()) {
      new GeneralCommandLine("/usr/bin/xdg-open", path).createProcess();
    }
    else if (SystemInfo.hasNautilus()) {
      new GeneralCommandLine("nautilus", path).createProcess();
    }
    else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      Desktop.getDesktop().open(new File(path));
    }
    else {
      Messages.showErrorDialog("This action isn't supported on the current platform", "Cannot Open File");
    }
  }

  @Nullable
  private static VirtualFile getFile(final AnActionEvent e) {
    return PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
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

      @Override
      public String getDoNotShowMessage() {
        return CommonBundle.message("dialog.options.do.not.ask");
      }
    };
    if (Messages.showOkCancelDialog(project, message, title, RevealFileAction.getActionName(),
                                    IdeBundle.message("action.close"), Messages.getInformationIcon(), option) == 0) {
      openFile(file);
    }
    return ref[0];
  }
}
