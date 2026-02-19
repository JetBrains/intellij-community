// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public abstract class DiffContentFactoryEx extends DiffContentFactory {
  public static @NotNull DiffContentFactoryEx getInstanceEx() {
    return (DiffContentFactoryEx)DiffContentFactory.getInstance();
  }


  public abstract @NotNull DocumentContentBuilder documentContent(@Nullable Project project, boolean readOnly);


  public abstract @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                                   byte @NotNull [] content,
                                                                   @NotNull FileType fileType,
                                                                   @NotNull String fileName);

  public abstract @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                                   byte @NotNull [] content,
                                                                   @NotNull FilePath filePath);

  public abstract @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                                   byte @NotNull [] content,
                                                                   @NotNull VirtualFile highlightFile);


  public interface DocumentContentBuilder {
    @NotNull DocumentContentBuilder withFileName(@Nullable String fileName);

    @NotNull DocumentContentBuilder withDefaultCharset(@Nullable Charset charset);

    @NotNull DocumentContentBuilder contextByFileType(@Nullable FileType fileType);

    @NotNull DocumentContentBuilder contextByFilePath(@Nullable FilePath filePath);

    @NotNull DocumentContentBuilder contextByHighlightFile(@Nullable VirtualFile file);

    @NotNull DocumentContentBuilder contextByReferent(@Nullable DocumentContent referent);

    @NotNull DocumentContentBuilder contextByProvider(@Nullable ContextProvider contextProvider);

    @NotNull DocumentContent buildFromText(@NotNull String text, boolean respectLineSeparators);

    @NotNull DocumentContent buildFromBytes(byte @NotNull [] content);
  }

  public interface ContextProvider {
    void passContext(@NotNull DocumentContentBuilder builder);
  }
}
