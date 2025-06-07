// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A rarely needed action that shows a popup with a path to a file (a list of parent directory names).
 * Clicking/hitting Enter on a directory opens it in a system file manager.
 *
 * @see RevealFileAction
 */
public final class ShowFilePathAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Disabled {
  @Override
  public void update(@NotNull AnActionEvent e) {
    var file = getFile(e);
    var visible = RevealFileAction.isSupported() && file != null && !LightVirtualFile.shouldSkipEventSystem(file);
    e.getPresentation().setVisible(visible);
    if (visible) {
      e.getPresentation().setEnabled(true);
      var isPopup = List.of(ActionPlaces.PROJECT_VIEW_POPUP, ActionPlaces.EDITOR_TAB_POPUP, ActionPlaces.BOOKMARKS_VIEW_POPUP).contains(e.getPlace());
      e.getPresentation().setText(ActionsBundle.message(isPopup ? "action.ShowFilePath.popup" : "action.ShowFilePath.text"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var file = getFile(e);
    if (file != null) {
      var asyncContext = Utils.createAsyncDataContext(e.getDataContext());
      show(file, popup -> popup.showInBestPositionFor(asyncContext));
    }
  }

  private static @Nullable VirtualFile getFile(AnActionEvent e) {
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

  private static void show(VirtualFile file, Consumer<ListPopup> action) {
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

    ReadAction
      .nonBlocking(() -> ContainerUtil.map(fileUrls, url -> {
        VirtualFile vFile = LocalFileSystem.getInstance().findFileByNioFile(Path.of(url));
        if (vFile == null) return EmptyIcon.ICON_16;
        if (vFile.isDirectory()) return AllIcons.Nodes.Folder;
        return FileTypeManager.getInstance().getFileTypeByFile(vFile).getIcon();
      }))
      .finishOnUiThread(ModalityState.nonModal(), icons -> action.accept(createPopup(files, icons)))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static String getPresentableUrl(VirtualFile file) {
    String url = file.getPresentableUrl();
    if (file.getParent() == null && SystemInfo.isWindows) url += "\\";
    return url;
  }

  private static ListPopup createPopup(List<VirtualFile> files, List<Icon> icons) {
    BaseListPopupStep<VirtualFile> step = new BaseListPopupStep<>(RevealFileAction.getActionName(), files, icons) {
      @Override
      public @NotNull String getTextFor(VirtualFile value) {
        return value.getPresentableName();
      }

      @Override
      public PopupStep<?> onChosen(VirtualFile selectedValue, boolean finalChoice) {
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

  /** @deprecated use {@link RevealFileAction#getFileManagerName} */
  @Deprecated(forRemoval = true)
  public static @NotNull String getFileManagerName() {
    return RevealFileAction.getFileManagerName();
  }

  /** @deprecated use {@link RevealFileAction#openFile} */
  @Deprecated(forRemoval = true)
  public static void openFile(@NotNull File file) {
    RevealFileAction.openFile(file);
  }

  /** @deprecated use {@link RevealFileAction#findLocalFile} */
  @Deprecated(forRemoval = true)
  public static @Nullable VirtualFile findLocalFile(@Nullable VirtualFile file) {
    return RevealFileAction.findLocalFile(file);
  }
  //</editor-fold>
}
