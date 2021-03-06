/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Two contents for general diff
 */
@Deprecated
public class SimpleDiffRequest extends DiffRequest {
  private final DiffContent[] myContents = new DiffContent[2];
  private final String[] myContentTitles = new String[2];
  private @Nls String myWindowTitle;

  public SimpleDiffRequest(Project project, @Nls String windowTitle) {
    super(project);
    myWindowTitle = windowTitle;
  }

  @Override
  public DiffContent @NotNull [] getContents() { return myContents; }

  @Override
  public String[] getContentTitles() { return myContentTitles; }

  @Nls
  @Override
  public String getWindowTitle() { return myWindowTitle; }

  public void setContents(@NotNull DiffContent content1, @NotNull DiffContent content2) {
    myContents[0] = content1;
    myContents[1] = content2;
  }

  public void setContentTitles(@Nls String title1, @Nls String title2) {
    myContentTitles[0] = title1;
    myContentTitles[1] = title2;
  }

  public void setWindowTitle(@Nls String windowTitle) {
    myWindowTitle = windowTitle;
  }

  public static SimpleDiffRequest compareFiles(VirtualFile file1, VirtualFile file2, Project project, @Nls String title) {
    FileDiffRequest result = new FileDiffRequest(project, title);
    result.myVirtualFiles[0] = file1;
    result.myVirtualFiles[1] = file2;
    result.myContentTitles[0] = DiffContentUtil.getTitle(file1);
    result.myContentTitles[1] = DiffContentUtil.getTitle(file2);
    return result;
  }

  public static SimpleDiffRequest compareFiles(@NotNull VirtualFile file1, @NotNull VirtualFile file2, @NotNull Project project) {
    final String title = DiffBundle.message("compare.file.vs.file.dialog.title", file1.getPresentableUrl(), file2.getPresentableUrl());
    return compareFiles(file1, file2, project, title);
  }

  private static class FileDiffRequest extends SimpleDiffRequest {
    private final String[] myContentTitles = new String[2];
    private final VirtualFile[] myVirtualFiles = new VirtualFile[2];

    FileDiffRequest(Project project, @Nls String title) {
      super(project, title);
    }

    @Override
    public DiffContent @NotNull [] getContents() {
      return new DiffContent[]{
        DiffContent.fromFile(getProject(), myVirtualFiles[0]),
        DiffContent.fromFile(getProject(), myVirtualFiles[1])
      };
    }

    @Override
    public String[] getContentTitles() {
      return myContentTitles;
    }
  }
}
