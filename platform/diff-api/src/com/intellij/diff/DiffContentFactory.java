// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

/*
 * Use ProgressManager.executeProcessUnderProgress() to pass modality state if needed
 */
public abstract class DiffContentFactory {
  public static @NotNull DiffContentFactory getInstance() {
    return ApplicationManager.getApplication().getService(DiffContentFactory.class);
  }

  /**
   * Content for the 'missing' side of addition/deletion diff request.
   */
  public abstract @NotNull EmptyContent createEmpty();


  public abstract @NotNull DocumentContent create(@NotNull String text);

  public abstract @NotNull DocumentContent create(@NotNull String text, @Nullable FileType type);

  public abstract @NotNull DocumentContent create(@NotNull String text, @Nullable FileType type, boolean respectLineSeparators);

  public abstract @NotNull DocumentContent create(@NotNull String text, @Nullable VirtualFile highlightFile);

  public abstract @NotNull DocumentContent create(@NotNull String text, @Nullable DocumentContent referent);


  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull String text);

  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type);

  /**
   * @param respectLineSeparators Whether {@link DocumentContent#getLineSeparator()} shall be set from {@code text} or be left 'Undefined'.
   */
  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type,
                                         boolean respectLineSeparators);

  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable VirtualFile highlightFile);

  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable DocumentContent referent);


  public abstract @NotNull DocumentContent createEditable(@Nullable Project project, @NotNull String text, @Nullable FileType fileType);

  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FilePath filePath);


  public abstract @NotNull DocumentContent create(@NotNull Document document, @Nullable DocumentContent referent);


  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document);

  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable FileType fileType);

  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable VirtualFile highlightFile);

  /**
   * @param referent content that should be used to infer highlighting and navigation from.
   *                 Ex: to be used for 'Compare File with Clipboard' action, as clipboard lacks context naturally.
   */
  public abstract @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable DocumentContent referent);


  public abstract @NotNull DiffContent create(@Nullable Project project, @NotNull VirtualFile file);

  public abstract @NotNull DiffContent create(@Nullable Project project, @NotNull VirtualFile file, @Nullable VirtualFile highlightFile);

  public abstract @Nullable DocumentContent createDocument(@Nullable Project project, @NotNull VirtualFile file);

  public abstract @Nullable FileContent createFile(@Nullable Project project, @NotNull VirtualFile file);


  public abstract @NotNull DocumentContent createFragment(@Nullable Project project, @NotNull Document document, @NotNull TextRange range);

  public abstract @NotNull DocumentContent createFragment(@Nullable Project project, @NotNull DocumentContent content, @NotNull TextRange range);


  public abstract @NotNull DiffContent createClipboardContent();

  public abstract @NotNull DocumentContent createClipboardContent(@Nullable DocumentContent referent);

  public abstract @NotNull DiffContent createClipboardContent(@Nullable Project project);

  public abstract @NotNull DocumentContent createClipboardContent(@Nullable Project project, @Nullable DocumentContent referent);


  public abstract @NotNull DiffContent createFromBytes(@Nullable Project project,
                                                       byte @NotNull [] content,
                                                       @NotNull FileType fileType,
                                                       @NotNull String fileName) throws IOException;

  public abstract @NotNull DiffContent createFromBytes(@Nullable Project project,
                                                       byte @NotNull [] content,
                                                       @NotNull FilePath filePath,
                                                       @Nullable Charset defaultCharset) throws IOException;

  public abstract @NotNull DiffContent createFromBytes(@Nullable Project project,
                                                       byte @NotNull [] content,
                                                       @NotNull FilePath filePath) throws IOException;

  public abstract @NotNull DiffContent createFromBytes(@Nullable Project project,
                                                       byte @NotNull [] content,
                                                       @NotNull VirtualFile highlightFile) throws IOException;

  public abstract @NotNull DiffContent createBinary(@Nullable Project project,
                                                    byte @NotNull [] content,
                                                    @NotNull FileType type,
                                                    @NotNull String fileName) throws IOException;
}
