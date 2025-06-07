// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Request to compare two or three files with each other.
 * Supported by the default tools, ex: {@link com.intellij.diff.tools.simple.SimpleDiffTool}.
 * <p>
 * 2 contents: left - right (before - after)
 * 3 contents: left - middle - right (local - base - server)
 */
public abstract class ContentDiffRequest extends DiffRequest {
  public abstract @NotNull List<DiffContent> getContents();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   * Titles could be null.
   */
  public abstract @NotNull List<@Nls String> getContentTitles();

  @Override
  public @NotNull @Unmodifiable List<VirtualFile> getFilesToRefresh() {
    List<VirtualFile> files = ContainerUtil.mapNotNull(getContents(), content -> {
      return content instanceof FileContent ? ((FileContent)content).getFile() : null;
    });
    return ContainerUtil.filter(files, file -> file.isInLocalFileSystem());
  }
}
