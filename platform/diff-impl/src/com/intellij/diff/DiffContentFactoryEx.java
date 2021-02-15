/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

public abstract class DiffContentFactoryEx extends DiffContentFactory {
  @NotNull
  public static DiffContentFactoryEx getInstanceEx() {
    return (DiffContentFactoryEx)DiffContentFactory.getInstance();
  }


  @NotNull
  public abstract DocumentContentBuilder documentContent(@Nullable Project project, boolean readOnly);


  @NotNull
  public abstract DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FilePath filePath);


  @NotNull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FilePath filePath) throws IOException;

  @NotNull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FilePath filePath,
                                              @Nullable Charset defaultCharset) throws IOException;

  @Override
  @NotNull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull VirtualFile highlightFile) throws IOException;


  @NotNull
  public abstract DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                          byte @NotNull [] content,
                                                          @NotNull FileType fileType,
                                                          @NotNull String fileName);

  @NotNull
  public abstract DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                          byte @NotNull [] content,
                                                          @NotNull FilePath filePath);

  @NotNull
  public abstract DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                          byte @NotNull [] content,
                                                          @NotNull VirtualFile highlightFile);


  public interface DocumentContentBuilder {
    @NotNull DocumentContentBuilder withFileName(@Nullable String fileName);

    @NotNull DocumentContentBuilder contextByFileType(@Nullable FileType fileType);

    @NotNull DocumentContentBuilder contextByFilePath(@Nullable FilePath filePath);

    @NotNull DocumentContentBuilder contextByHighlightFile(@Nullable VirtualFile file);

    @NotNull DocumentContentBuilder contextByReferent(@Nullable DocumentContent referent);

    @NotNull DocumentContent buildFromText(@NotNull String text, boolean respectLineSeparators);

    @NotNull DocumentContent buildFromBytes(byte @NotNull [] content, @NotNull Charset charset);
  }
}
