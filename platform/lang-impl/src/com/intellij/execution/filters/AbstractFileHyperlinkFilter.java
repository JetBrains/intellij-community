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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.LocalFileFinder;

import java.io.File;
import java.util.List;

public abstract class AbstractFileHyperlinkFilter implements Filter {
  private static final Logger LOG = Logger.getInstance(AbstractFileHyperlinkFilter.class);

  private final Project myProject;
  private final ProjectFileIndex myFileIndex;
  private final VirtualFile myBaseDir;

  public AbstractFileHyperlinkFilter(@NotNull Project project, @Nullable String baseDir) {
    this(project, findDir(baseDir));
  }

  public AbstractFileHyperlinkFilter(@NotNull Project project, @Nullable VirtualFile baseDir) {
    myProject = project;
    myFileIndex = ProjectFileIndex.getInstance(project);
    myBaseDir = baseDir;
  }

  @Nullable
  protected static VirtualFile findDir(@Nullable String baseDir) {
    if (baseDir == null) {
      return null;
    }
    return ReadAction.compute(() -> {
      String path = FileUtil.toSystemIndependentName(baseDir);
      VirtualFile dir = LocalFileFinder.findFile(path);
      return dir != null && dir.isValid() && dir.isDirectory() ? dir : null;
    });
  }

  protected boolean supportVfsRefresh() {
    return false;
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
      if (StringUtil.isEmptyOrSpaces(filePath)) continue;
      VirtualFile file = findFile(filePath);
      HyperlinkInfo info = null;
      boolean grayedHyperLink = false;
      if (file != null) {
        info = new OpenFileHyperlinkInfo(myProject, file, link.getDocumentLine(), link.getDocumentColumn());
        grayedHyperLink = isGrayedHyperlink(file);
      }
      else if (supportVfsRefresh()) {
         File ioFile = findIoFile(filePath);
        if (ioFile != null) {
          info = new MyFileHyperlinkInfo(ioFile, link.getDocumentLine(), link.getDocumentColumn());
        }
      }
      if (info != null) {
        int offset = entireLength - line.length();
        items.add(new Filter.ResultItem(offset + link.getHyperlinkStartInd(),
                                        offset + link.getHyperlinkEndInd(),
                                        info,
                                        grayedHyperLink));
      }
    }
    return items.isEmpty() ? null : new Result(items);
  }

  @Nullable
  private File findIoFile(@NotNull String filePath) {
    File ioFile = new File(filePath);
    if (ioFile.isAbsolute() && ioFile.isFile()) {
      return ioFile;
    }
    if (myBaseDir != null) {
      ioFile = new File(myBaseDir.getPath(), filePath);
      if (ioFile.isFile()) {
        return ioFile;
      }
    }
    return null;
  }

  private boolean isGrayedHyperlink(@NotNull VirtualFile file) {
    return !myFileIndex.isInContent(file) || myFileIndex.isInLibrary(file);
  }

  @NotNull
  public abstract List<FileHyperlinkRawData> parse(@NotNull String line);

  @Nullable
  public VirtualFile findFile(@NotNull String filePath) {
    VirtualFile file = LocalFileFinder.findFile(filePath);
    if (file == null && myBaseDir != null && myBaseDir.isValid()) {
      file = myBaseDir.findFileByRelativePath(filePath);
    }
    return file;
  }

  private static class MyFileHyperlinkInfo implements HyperlinkInfo {

    private final File myIoFile;
    private final int myDocumentLine;
    private final int myDocumentColumn;
    private Ref<VirtualFile> myFileRef;

    public MyFileHyperlinkInfo(@NotNull File ioFile, int documentLine, int documentColumn) {
      myIoFile = ioFile;
      myDocumentLine = documentLine;
      myDocumentColumn = documentColumn;
    }

    @Override
    public void navigate(Project project) {
      Ref<VirtualFile> fileRef = myFileRef;
      if (fileRef == null) {
        VirtualFile file = WriteAction.compute(() -> VfsUtil.findFileByIoFile(myIoFile, true));
        fileRef = Ref.create(file);
        myFileRef = fileRef;
      }
      if (fileRef.get() != null) {
        OpenFileHyperlinkInfo linkInfo = new OpenFileHyperlinkInfo(project, fileRef.get(), myDocumentLine, myDocumentColumn);
        linkInfo.navigate(project);
      }
    }
  }
}
