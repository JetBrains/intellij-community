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
package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffRequestFactory {
  @NotNull
  public static DiffRequest createFromFile(@Nullable Project project, @NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    DiffContent content1 = DiffContentFactory.create(project, file1);
    DiffContent content2 = DiffContentFactory.create(project, file2);

    String title1 = getVirtualFileContentTitle(file1);
    String title2 = getVirtualFileContentTitle(file2);

    String title = DiffBundle.message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
                                      file1.getName(), file2.getName());

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }

  @NotNull
  public static String getVirtualFileContentTitle(@NotNull VirtualFile file) {
    String name = file.getName();
    VirtualFile parent = file.getParent();
    if (parent != null) {
      return name + " (" + FileUtil.toSystemDependentName(parent.getPath()) + ")";
    }
    return name;
  }

  @NotNull
  public static DiffRequest createClipboardVsValue(@NotNull String value) {
    DiffContent content1 = DiffContentFactory.createClipboardContent();
    DiffContent content2 = DiffContentFactory.create(value, null);

    String title1 = DiffBundle.message("diff.content.clipboard.content.title");
    String title2 = DiffBundle.message("diff.content.selected.value");

    String title = DiffBundle.message("diff.clipboard.vs.value.dialog.title");

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }
}
