// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.merge.ConflictType;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
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
    return ApplicationManager.getApplication().getService(DiffRequestFactory.class);
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

  @NlsContexts.Label
  @Nullable
  @Contract("null->null; !null->!null")
  public abstract String getContentTitle(@Nullable VirtualFile file);

  @NlsContexts.DialogTitle
  @NotNull
  public abstract String getTitle(@Nullable VirtualFile file1, @Nullable VirtualFile file2);

  @NlsContexts.DialogTitle
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
                                                  @Nullable @NlsContexts.DialogTitle String title,
                                                  @NotNull List<@NlsContexts.Label String> titles,
                                                  @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable @NlsContexts.DialogTitle String title,
                                                  @NotNull List<@NlsContexts.Label String> contentTitles,
                                                  @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable ConflictType conflictType,
                                                  @Nullable @NlsContexts.DialogTitle String title,
                                                  @NotNull List<@NlsContexts.Label String> contentTitles,
                                                  @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;


  @NotNull
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable @NlsContexts.DialogTitle String title,
                                                  @NotNull List<@NlsContexts.Label String> contentTitles)
    throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable ConflictType conflictType,
                                                  @Nullable @NlsContexts.DialogTitle String title,
                                                  @NotNull List<@NlsContexts.Label String> contentTitles)
  throws InvalidDiffRequestException;

  @NotNull
  public abstract TextMergeRequest createTextMergeRequest(@Nullable Project project,
                                                          @NotNull VirtualFile output,
                                                          @NotNull List<byte[]> byteContents,
                                                          @Nullable @NlsContexts.DialogTitle String title,
                                                          @NotNull List<@NlsContexts.Label String> contentTitles,
                                                          @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract TextMergeRequest createTextMergeRequest(@Nullable Project project,
                                                          @NotNull VirtualFile output,
                                                          @NotNull List<byte[]> byteContents,
                                                          @Nullable ConflictType conflictType,
                                                          @Nullable @NlsContexts.DialogTitle String title,
                                                          @NotNull List<@NlsContexts.Label String> contentTitles,
                                                          @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createBinaryMergeRequest(@Nullable Project project,
                                                        @NotNull VirtualFile output,
                                                        @NotNull List<byte[]> byteContents,
                                                        @Nullable @NlsContexts.DialogTitle String title,
                                                        @NotNull List<@NlsContexts.Label String> contentTitles,
                                                        @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                           @NotNull VirtualFile output,
                                                           @NotNull List<? extends VirtualFile> contents,
                                                           @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                           @NotNull VirtualFile output,
                                                           @NotNull List<? extends VirtualFile> contents,
                                                           @Nullable @NlsContexts.DialogTitle String title,
                                                           @NotNull List<@NlsContexts.Label String> contentTitles,
                                                           @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;

  @NotNull
  public abstract TextMergeRequest createTextMergeRequestFromFiles(@Nullable Project project,
                                                                   @NotNull VirtualFile output,
                                                                   @NotNull List<? extends VirtualFile> contents,
                                                                   @Nullable @NlsContexts.DialogTitle String title,
                                                                   @NotNull List<@NlsContexts.Label String> contentTitles,
                                                                   @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException;
}
