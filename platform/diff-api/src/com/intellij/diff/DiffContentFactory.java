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
package com.intellij.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(DiffContentFactory.class);
  }

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
                                              @NotNull byte[] content,
                                              @NotNull FileType fileType,
                                              @NotNull String fileName) throws IOException;

  @NotNull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              @NotNull byte[] content,
                                              @NotNull VirtualFile highlightFile) throws IOException;

  @NotNull
  public abstract DiffContent createBinary(@Nullable Project project,
                                           @NotNull byte[] content,
                                           @NotNull FileType type,
                                           @NotNull String fileName) throws IOException;


  @NotNull
  @Deprecated
  public DiffContent createFromBytes(@Nullable Project project,
                                     @NotNull VirtualFile highlightFile,
                                     @NotNull byte[] content) throws IOException {
    return createFromBytes(project, content, highlightFile);
  }

  @NotNull
  @Deprecated
  public DiffContent createBinary(@Nullable Project project,
                                  @NotNull String fileName,
                                  @NotNull FileType type,
                                  @NotNull byte[] content) throws IOException {
    return createBinary(project, content, type, fileName);
  }
}
