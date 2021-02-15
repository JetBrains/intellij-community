// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/*
 * Use ProgressManager.executeProcessUnderProgress() to pass modality state if needed
 */
public abstract class DiffContentFactory {
  @NotNull
  public static DiffContentFactory getInstance() {
    return ApplicationManager.getApplication().getService(DiffContentFactory.class);
  }

  /**
   * Content for the 'missing' side of addition/deletion diff request.
   */
  @NotNull
  public abstract EmptyContent createEmpty();


  @NotNull
  public abstract DocumentContent create(@NotNull String text);

  @NotNull
  public abstract DocumentContent create(@NotNull String text, @Nullable FileType type);

  @NotNull
  public abstract DocumentContent create(@NotNull String text, @Nullable FileType type, boolean respectLineSeparators);

  @NotNull
  public abstract DocumentContent create(@NotNull String text, @Nullable VirtualFile highlightFile);

  @NotNull
  public abstract DocumentContent create(@NotNull String text, @Nullable DocumentContent referent);


  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull String text);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type,
                                         boolean respectLineSeparators);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable VirtualFile highlightFile);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable DocumentContent referent);


  @NotNull
  public abstract DocumentContent createEditable(@Nullable Project project, @NotNull String text, @Nullable FileType fileType);


  @NotNull
  public abstract DocumentContent create(@NotNull Document document, @Nullable DocumentContent referent);


  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull Document document);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable FileType fileType);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable VirtualFile highlightFile);

  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable DocumentContent referent);


  @NotNull
  public abstract DiffContent create(@Nullable Project project, @NotNull VirtualFile file);

  @Nullable
  public abstract DocumentContent createDocument(@Nullable Project project, @NotNull VirtualFile file);

  @Nullable
  public abstract FileContent createFile(@Nullable Project project, @NotNull VirtualFile file);


  @NotNull
  public abstract DocumentContent createFragment(@Nullable Project project, @NotNull Document document, @NotNull TextRange range);

  @NotNull
  public abstract DocumentContent createFragment(@Nullable Project project, @NotNull DocumentContent content, @NotNull TextRange range);


  @NotNull
  public abstract DiffContent createClipboardContent();

  @NotNull
  public abstract DocumentContent createClipboardContent(@Nullable DocumentContent referent);

  @NotNull
  public abstract DiffContent createClipboardContent(@Nullable Project project);

  @NotNull
  public abstract DocumentContent createClipboardContent(@Nullable Project project, @Nullable DocumentContent referent);


  @NotNull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FileType fileType,
                                              @NotNull String fileName) throws IOException;

  @NotNull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull VirtualFile highlightFile) throws IOException;

  @NotNull
  public abstract DiffContent createBinary(@Nullable Project project,
                                           byte @NotNull [] content,
                                           @NotNull FileType type,
                                           @NotNull String fileName) throws IOException;
}
