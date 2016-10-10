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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.LocalFileFinder;

import java.util.List;

public abstract class AbstractFileHyperlinkFilter implements Filter {
  private static final Logger LOG = Logger.getInstance(AbstractFileHyperlinkFilter.class);

  private final Project myProject;
  private final ProjectFileIndex myFileIndex;
  private final VirtualFile myBaseDir;

  public AbstractFileHyperlinkFilter(@NotNull Project project, @Nullable String baseDir) {
    myProject = project;
    myFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    myBaseDir = findDir(baseDir);
  }

  @Nullable
  private static VirtualFile findDir(@Nullable String baseDir) {
    if (baseDir == null) {
      return null;
    }
    return ReadAction.compute(() -> {
      String path = FileUtil.toSystemIndependentName(baseDir);
      VirtualFile dir = LocalFileFinder.findFile(path);
      return dir != null && dir.isValid() && dir.isDirectory() ? dir : null;
    });
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
      String filePath = FileUtil.toSystemIndependentName(link.getFilePath());
      VirtualFile file = StringUtil.isEmptyOrSpaces(filePath) ? null : findFile(filePath);
      if (file != null) {
        OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject,
                                                               file,
                                                               link.getDocumentLine(),
                                                               link.getDocumentColumn());
        boolean grayedHyperLink = isGrayedHyperlink(file);
        int offset = entireLength - line.length();
        items.add(new Filter.ResultItem(offset + link.getHyperlinkStartInd(),
                                        offset + link.getHyperlinkEndInd(),
                                        info,
                                        grayedHyperLink));
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
    VirtualFile file = LocalFileFinder.findFile(filePath);
    if (file == null && myBaseDir != null) {
      file = myBaseDir.findFileByRelativePath(filePath);
    }
    return file;
  }
}
