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

import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/*
 * Use ProgressManager.executeProcessUnderProgress() to pass modality state if needed
 */
public abstract class DiffRequestFactory {
  @NotNull
  public static DiffRequestFactory getInstance() {
    return ServiceManager.getService(DiffRequestFactory.class);
  }

  @NotNull
  public abstract ContentDiffRequest createFromFiles(@Nullable Project project, @NotNull VirtualFile file1, @NotNull VirtualFile file2);

  @NotNull
  public abstract ContentDiffRequest createClipboardVsValue(@NotNull String value);


  @NotNull
  public abstract String getContentTitle(@NotNull VirtualFile file);

  @NotNull
  public abstract String getTitle(@NotNull VirtualFile file1, @NotNull VirtualFile file2);

  @NotNull
  public abstract String getTitle(@NotNull VirtualFile file);

  @Nullable
  public abstract MergeRequest createMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable String title,
                                                  @NotNull List<String> contentTitles,
                                                  @Nullable Consumer<MergeResult> applyCallback);
}
