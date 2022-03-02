// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A rarely needed action that shows a popup with a path to a file (a list of parent directory names).
 * Clicking/hitting enter on a directory opens it in a system file manager.
 *
 * @see RevealFileAction
 */
public class ShowFilePathAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean visible = !SystemInfo.isMac && RevealFileAction.isSupported();
    e.getPresentation().setVisible(visible);
    if (visible) {
      VirtualFile file = getFile(e);
      e.getPresentation().setEnabled(file != null);
      e.getPresentation().setText(ActionsBundle.messagePointer(file != null && file.isDirectory() ? "action.ShowFilePath.directory" : "action.ShowFilePath.file"));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = getFile(e);
    if (file != null) {
      show(file, popup -> {
        DataManager dataManager = DataManager.getInstance();
        if (dataManager != null) {
          dataManager.getDataContextFromFocusAsync().onSuccess((popup::showInBestPositionFor));
        }
      });
    }
  }

  @Nullable
  private static VirtualFile getFile(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files == null || files.length == 1 ? e.getData(CommonDataKeys.VIRTUAL_FILE) : null;
  }

  public static void show(@NotNull VirtualFile file, @NotNull MouseEvent e) {
    show(file, popup -> {
      if (e.getComponent().isShowing()) {
        popup.show(new RelativePoint(e));
      }
    });
  }

  private static void show(@NotNull VirtualFile file, @NotNull Consumer<? super ListPopup> action) {
    if (!RevealFileAction.isSupported()) return;

    List<VirtualFile> files = new ArrayList<>();
    List<String> fileUrls = new ArrayList<>();
    VirtualFile eachParent = file;
    while (eachParent != null) {
      int index = files.size();
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
      ApplicationManager.getApplication().invokeLater(() -> action.consume(createPopup(files, icons)));
    });
  }

  private static String getPresentableUrl(VirtualFile file) {
    String url = file.getPresentableUrl();
    if (file.getParent() == null && SystemInfo.isWindows) url += "\\";
    return url;
  }

  private static ListPopup createPopup(List<? extends VirtualFile> files, List<? extends Icon> icons) {
    BaseListPopupStep<VirtualFile> step = new BaseListPopupStep<>(RevealFileAction.getActionName(), files, icons) {
      @NotNull
      @Override
      public String getTextFor(VirtualFile value) {
        return value.getPresentableName();
      }

      @Override
      public PopupStep onChosen(VirtualFile selectedValue, boolean finalChoice) {
        File selectedFile = new File(getPresentableUrl(selectedValue));
        if (selectedFile.exists()) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> RevealFileAction.openFile(selectedFile));
        }
        return FINAL_CHOICE;
      }
    };

    return JBPopupFactory.getInstance().createListPopup(step);
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link RevealFileAction#FILE_SELECTING_LISTENER} (to be removed in IDEA 2021.3) */
  @Deprecated(forRemoval = true)
  public static final NotificationListener FILE_SELECTING_LISTENER = RevealFileAction.FILE_SELECTING_LISTENER;

  /** @deprecated use {@link RevealFileAction#getFileManagerName} (to be removed in IDEA 2021.3) */
  @Deprecated(forRemoval = true)
  public static @NotNull String getFileManagerName() {
    return RevealFileAction.getFileManagerName();
  }

  /** @deprecated use {@link RevealFileAction#openFile} (to be removed in IDEA 2021.3) */
  @Deprecated(forRemoval = true)
  public static void openFile(@NotNull File file) {
    RevealFileAction.openFile(file);
  }

  /** @deprecated use {@link RevealFileAction#openDirectory} (to be removed in IDEA 2021.3) */
  @Deprecated(forRemoval = true)
  public static void openDirectory(@NotNull File directory) {
    RevealFileAction.openDirectory(directory);
  }

  /** @deprecated use {@link RevealFileAction#findLocalFile} (to be removed in IDEA 2021.3) */
  @Deprecated(forRemoval = true)
  public static @Nullable VirtualFile findLocalFile(@Nullable VirtualFile file) {
    return RevealFileAction.findLocalFile(file);
  }
  //</editor-fold>
}
