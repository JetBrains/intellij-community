// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * Consider using {@link BaseShowDiffAction} instead.
 */
public class CompareFilesAction extends BaseShowDiffAction {

  /**
   * @deprecated use generic {@link DiffDataKeys#DIFF_REQUEST_TO_COMPARE}
   */
  @Deprecated
  public static final DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("CompareFilesAction.DiffRequest");

  public static final @NonNls String LAST_USED_FILE_KEY = "two.files.diff.last.used.file";
  public static final @NonNls String LAST_USED_FOLDER_KEY = "two.files.diff.last.used.folder";

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    String text = ActionsBundle.message("action.compare.files.text");

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null) {
      if (files.length == 1) {
        text = ActionsBundle.message("action.compare.with.text");
      }
      else if (files.length == 2 || files.length == 3) {
        Set<Type> types = ContainerUtil.map2Set(files, CompareFilesAction::getType);
        if (types.size() != 1) {
          text = ActionsBundle.message("action.compare.text");
        }
        else {
          text = ActionsBundle.message(switch (types.iterator().next()) {
            case FILE -> "action.compare.files.text";
            case DIRECTORY -> "action.CompareDirs.text";
            case ARCHIVE -> "action.compare.archives.text";
          });
        }
      }
    }

    e.getPresentation().setText(text);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST_TO_COMPARE);
    if (request != null) {
      return true;
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) {
      return false;
    }

    if (files.length == 0 || files.length > 3) return false;
    boolean hasContent = ContainerUtil.all(Arrays.asList(files), BaseShowDiffAction::hasContent);
    if (!hasContent) return false;

    if (files.length == 3) {
      Set<Type> types = ContainerUtil.map2Set(files, CompareFilesAction::getType);
      if (types.contains(Type.DIRECTORY) || types.contains(Type.ARCHIVE)) return false;
    }

    return true;
  }

  protected @Nullable DiffRequest getDiffRequest(@NotNull AnActionEvent e) {
    return e.getData(DiffDataKeys.DIFF_REQUEST_TO_COMPARE);
  }

  @Override
  protected @Nullable DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    DiffRequest diffRequest = getDiffRequest(e);
    if (diffRequest != null) {
      return new SimpleDiffRequestChain(diffRequest);
    }

    VirtualFile file1;
    VirtualFile file2;
    VirtualFile baseFile = null;

    VirtualFile[] files = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files.length == 1) {
      file1 = files[0];
      file2 = getOtherFile(project, file1);
      if (file2 == null || !hasContent(file2)) return null;
    }
    else if (files.length == 2) {
      file1 = files[0];
      file2 = files[1];
    }
    else {
      file1 = files[0];
      baseFile = files[1];
      file2 = files[2];
    }

    // getOtherFile() shows dialog that can invalidate files
    if (!file1.isValid() || !file2.isValid() || (baseFile != null && !baseFile.isValid())) return null;

    Set<Type> types = ContainerUtil.map2Set(files, CompareFilesAction::getType);
    if (types.contains(Type.DIRECTORY)) FeatureUsageTracker.getInstance().triggerFeatureUsed("dir.diff");
    if (types.contains(Type.ARCHIVE)) FeatureUsageTracker.getInstance().triggerFeatureUsed("jar.diff");
    if ("ipynb".equals(file1.getExtension()) && "ipynb".equals(file2.getExtension())) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("jupyter.compare.notebooks");
    }

    return createMutableChainFromFiles(project, file1, file2, baseFile);
  }

  private static @Nullable VirtualFile getOtherFile(@Nullable Project project, @NotNull VirtualFile file) {
    FileChooserDescriptor descriptor;
    String key;

    Type type = getType(file);
    if (type == Type.DIRECTORY || type == Type.ARCHIVE) {
      descriptor = new FileChooserDescriptor(false, true, true, true, true, false);
      key = LAST_USED_FOLDER_KEY;
    }
    else {
      descriptor = new FileChooserDescriptor(true, false, false, true, true, false);
      key = LAST_USED_FILE_KEY;
    }
    VirtualFile selectedFile = getDefaultSelection(project, key, file);
    VirtualFile otherFile = FileChooser.chooseFile(descriptor.withTitle(DiffBundle.message("select.file.to.compare")), project, selectedFile);
    if (otherFile != null) updateDefaultSelection(project, key, otherFile);
    return otherFile;
  }

  private static @NotNull VirtualFile getDefaultSelection(@Nullable Project project, @NotNull String key, @NotNull VirtualFile file) {
    if (project == null) return file;
    final String path = PropertiesComponent.getInstance(project).getValue(key);
    if (path == null) return file;
    VirtualFile lastSelection = LocalFileSystem.getInstance().findFileByPath(path);
    return lastSelection != null ? lastSelection : file;
  }

  private static void updateDefaultSelection(@Nullable Project project, @NotNull String key, @NotNull VirtualFile file) {
    if (project == null) return;
    PropertiesComponent.getInstance(project).setValue(key, file.getPath());
  }

  private static @NotNull Type getType(@Nullable VirtualFile file) {
    if (file == null) return Type.FILE;
    if (file.getFileType() instanceof ArchiveFileType) return Type.ARCHIVE;
    if (file.isDirectory()) return Type.DIRECTORY;
    return Type.FILE;
  }

  private enum Type {FILE, DIRECTORY, ARCHIVE}
}
