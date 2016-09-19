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
package com.intellij.execution.filters;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public abstract class AbstractFileHyperlinkFilter implements Filter {
  private static final Logger LOG = Logger.getInstance(AbstractFileHyperlinkFilter.class);

  private final Project myProject;
  private final ProjectFileIndex myFileIndex;
  private final String myBaseDir;

  public AbstractFileHyperlinkFilter(@NotNull Project project, @Nullable String baseDir) {
    myProject = project;
    myFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    myBaseDir = baseDir;
  }

  @Nullable
  @Override
  public final Result applyFilter(String line, int entireLength) {
    List<FileHyperlinkRawData> links;
    try {
      links = parse(line);
    }
    catch (RuntimeException e) {
      LOG.error("Failed to parse '" + line + "' with " + getClass(), e);
      return null;
    }
    List<Filter.ResultItem> items = ContainerUtil.newArrayList();
    for (FileHyperlinkRawData link : links) {
      VirtualFile file = findFile(link.getFilePath());
      if (file != null) {
        OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject,
                                                               file,
                                                               link.getDocumentLine(),
                                                               link.getDocumentColumn());
        boolean grayedHyperLink = isGrayedHyperlink(file);
        items.add(new Filter.ResultItem(link.getHyperlinkStartInd(), link.getHyperlinkEndInd(), info, grayedHyperLink));
      }
    }
    return items.isEmpty() ? null : new Result(items);
  }

  private boolean isGrayedHyperlink(@NotNull VirtualFile file) {
    return !myFileIndex.isInContent(file) || myFileIndex.isInLibrary(file);
  }

  @NotNull
  public abstract List<FileHyperlinkRawData> parse(@NotNull String line);

  @Nullable
  public VirtualFile findFile(@NotNull String filePath) {
    File file = findIoFile(filePath);
    if (file != null) {
      return LocalFileSystem.getInstance().findFileByIoFile(file);
    }
    return null;
  }

  @Nullable
  private File findIoFile(@NotNull String filePath) {
    File file = new File(filePath);
    if (file.isFile() && file.isAbsolute()) {
      return file;
    }
    if (myBaseDir != null) {
      file = new File(myBaseDir, filePath);
      if (file.isFile()) {
        return file;
      }
    }
    return null;
  }
}
