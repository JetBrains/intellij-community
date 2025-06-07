// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.diff.DiffContext;
import com.intellij.diff.tools.ErrorDiffTool;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnknownFileTypeDiffRequest extends ComponentDiffRequest {
  private final @Nullable String myFileName;
  private final @Nullable @Nls String myTitle;

  public UnknownFileTypeDiffRequest(@NotNull VirtualFile file, @Nullable @NlsContexts.DialogTitle String title) {
    this(file.getName(), title);
  }

  public UnknownFileTypeDiffRequest(@NotNull String fileName, @Nullable @NlsContexts.DialogTitle String title) {
    boolean knownFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName) != UnknownFileType.INSTANCE;
    myFileName = knownFileType ? null : fileName;
    myTitle = title;
  }

  @Override
  public @NotNull JComponent getComponent(final @NotNull DiffContext context) {
    return createComponent(myFileName, context);
  }

  public static @NotNull JComponent createComponent(@Nullable String fileName, @Nullable DiffContext context) {
    String message = DiffBundle.message("error.cant.show.diff.for.unknown.file");
    if (fileName == null) return DiffUtil.createMessagePanel(message);
    return ErrorDiffTool.createReloadMessagePanel(context, message, DiffBundle.message("button.associate.file.type"),
                                                  () -> FileTypeChooser.associateFileType(fileName));
  }

  public @Nullable String getFileName() {
    return myFileName;
  }

  @Override
  public @Nullable String getTitle() {
    return myTitle;
  }
}
