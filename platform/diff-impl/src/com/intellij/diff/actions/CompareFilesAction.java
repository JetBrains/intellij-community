/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions;

import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareFilesAction extends BaseShowDiffAction {
  public static final DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("CompareFilesAction.DiffRequest");

  public static final String LAST_USED_FILE_KEY = "two.files.diff.last.used.file";
  public static final String LAST_USED_FOLDER_KEY = "two.files.diff.last.used.folder";

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    String text = "Compare Files";
    if (files != null && files.length == 1) {
      text = "Compare With...";
    }
    else if (files != null && files.length == 2) {
      Type type1 = getType(files[0]);
      Type type2 = getType(files[1]);

      if (type1 != type2) {
        text = "Compare";
      }
      else {
        switch (type1) {
          case FILE:
            text = "Compare Files";
            break;
          case DIRECTORY:
            text = "Compare Directories";
            break;
          case ARCHIVE:
            text = "Compare Archives";
            break;
        }
      }
    }
    e.getPresentation().setText(text);
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    DiffRequest request = e.getData(DIFF_REQUEST);
    if (request != null) {
      return true;
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) {
      return false;
    }

    if (files.length == 1) {
      return hasContent(files[0]);
    }
    else if (files.length == 2) {
      return hasContent(files[0]) && hasContent(files[1]);
    }
    else {
      return false;
    }
  }

  @Nullable
  @Override
  protected DiffRequest getDiffRequest(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    DiffRequest diffRequest = e.getData(DIFF_REQUEST);
    if (diffRequest != null) {
      return diffRequest;
    }

    VirtualFile file1;
    VirtualFile file2;

    VirtualFile[] data = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (data.length == 1) {
      file1 = data[0];
      file2 = getOtherFile(project, file1);
      if (file2 == null || !hasContent(file2)) return null;
    }
    else {
      file1 = data[0];
      file2 = data[1];
    }

    if (!file1.isValid() || !file2.isValid()) return null; // getOtherFile() shows dialog that can invalidate files

    Type type1 = getType(file1);
    Type type2 = getType(file2);
    if (type1 == Type.DIRECTORY || type2 == Type.DIRECTORY) FeatureUsageTracker.getInstance().triggerFeatureUsed("dir.diff");
    if (type1 == Type.ARCHIVE || type2 == Type.ARCHIVE) FeatureUsageTracker.getInstance().triggerFeatureUsed("jar.diff");

    return DiffRequestFactory.getInstance().createFromFiles(project, file1, file2);
  }

  @Nullable
  private static VirtualFile getOtherFile(@Nullable Project project, @NotNull VirtualFile file) {
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
    VirtualFile otherFile = FileChooser.chooseFile(descriptor, project, selectedFile);
    if (otherFile != null) updateDefaultSelection(project, key, otherFile);
    return otherFile;
  }

  @NotNull
  private static VirtualFile getDefaultSelection(@Nullable Project project, @NotNull String key, @NotNull VirtualFile file) {
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

  @NotNull
  private static Type getType(@Nullable VirtualFile file) {
    if (file == null) return Type.FILE;
    if (file.getFileType() instanceof ArchiveFileType) return Type.ARCHIVE;
    if (file.isDirectory()) return Type.DIRECTORY;
    return Type.FILE;
  }

  private enum Type {FILE, DIRECTORY, ARCHIVE}
}
