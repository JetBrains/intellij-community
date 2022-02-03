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

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 2 contents: left - right (before - after)
 * 3 contents: left - middle - right (local - base - server)
 */
public abstract class ContentDiffRequest extends DiffRequest {
  @NotNull
  public abstract List<DiffContent> getContents();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   * Titles could be null.
   */
  @NotNull
  public abstract List<@Nls String> getContentTitles();

  @NotNull
  @Override
  public List<VirtualFile> getFilesToRefresh() {
    List<VirtualFile> files = ContainerUtil.map(getContents(), content -> {
      return content instanceof FileContent ? ((FileContent)content).getFile() : null;
    });
    return ContainerUtil.filter(files, file -> file.isInLocalFileSystem());
  }
}
