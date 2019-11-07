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
package com.intellij.diff.requests;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.tools.ErrorDiffTool;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnknownFileTypeDiffRequest extends ComponentDiffRequest {
  @Nullable private final String myFileName;
  @Nullable private final String myTitle;

  public UnknownFileTypeDiffRequest(@NotNull VirtualFile file, @Nullable String title) {
    this(file.getName(), title);
  }

  public UnknownFileTypeDiffRequest(@NotNull String fileName, @Nullable String title) {
    boolean knownFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName) != UnknownFileType.INSTANCE;
    myFileName = knownFileType ? null : fileName;
    myTitle = title;
  }

  @NotNull
  @Override
  public JComponent getComponent(@NotNull final DiffContext context) {
    return createComponent(myFileName, context);
  }

  @NotNull
  public static JComponent createComponent(@Nullable String fileName, @Nullable DiffContext context) {
    String message = "Can't show diff for unknown file type.";
    if (fileName == null) return DiffUtil.createMessagePanel(message);
    return ErrorDiffTool.createReloadMessagePanel(context, message, "Associate",
                                                  () -> FileTypeChooser.associateFileType(fileName));
  }

  @Nullable
  public String getFileName() {
    return myFileName;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  private static void tryReloadRequest(@NotNull DiffContext context) {
    if (context instanceof DiffContextEx) ((DiffContextEx)context).reloadDiffRequest();
  }
}
