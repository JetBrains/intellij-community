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

import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Use ProgressManager.executeProcessUnderProgress() to pass modality state if needed
 */
public abstract class DiffRequestFactory {
  @NotNull
  public static DiffRequestFactory getInstance() {
    return ServiceManager.getService(DiffRequestFactory.class);
  }

  //
  // Diff
  //

  @NotNull
  public abstract ContentDiffRequest createFromFiles(@Nullable Project project, @Nullable VirtualFile file1, @Nullable VirtualFile file2);

  @NotNull
  public abstract ContentDiffRequest createFromFiles(@Nullable Project project,
                                                     @NotNull VirtualFile leftFile,
                                                     @NotNull VirtualFile baseFile,
                                                     @NotNull VirtualFile rightFile);

  @NotNull
  public abstract ContentDiffRequest createClipboardVsValue(@NotNull String value);

  //
  // Titles
  //

  @Nullable
  @Contract("null->null; !null->!null")
  public abstract String getContentTitle(@Nullable VirtualFile file);

  @NotNull
  public abstract String getTitle(@Nullable VirtualFile file1, @Nullable VirtualFile file2);

  @NotNull
  public abstract String getTitle(@NotNull VirtualFile file);

  //
  // Merge
  //

  @NotNull
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @Nullable FileType fileType,
                                                  @NotNull Document output,
                                                  @NotNull List<String> textContents,
                                                  @Nullable String title,
                                                  @NotNull List<String> titles,
                                                  @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable String title,
                                                  @NotNull List<String> contentTitles,
                                                  @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract TextMergeRequest createTextMergeRequest(@Nullable Project project,
                                                          @NotNull VirtualFile output,
                                                          @NotNull List<byte[]> byteContents,
                                                          @Nullable String title,
                                                          @NotNull List<String> contentTitles,
                                                          @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createBinaryMergeRequest(@Nullable Project project,
                                                        @NotNull VirtualFile output,
                                                        @NotNull List<byte[]> byteContents,
                                                        @Nullable String title,
                                                        @NotNull List<String> contentTitles,
                                                        @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                           @NotNull VirtualFile output,
                                                           @NotNull List<VirtualFile> contents,
                                                           @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                           @NotNull VirtualFile output,
                                                           @NotNull List<VirtualFile> contents,
                                                           @Nullable String title,
                                                           @NotNull List<String> contentTitles,
                                                           @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract TextMergeRequest createTextMergeRequestFromFiles(@Nullable Project project,
                                                                   @NotNull VirtualFile output,
                                                                   @NotNull List<VirtualFile> contents,
                                                                   @Nullable String title,
                                                                   @NotNull List<String> contentTitles,
                                                                   @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException;
}
