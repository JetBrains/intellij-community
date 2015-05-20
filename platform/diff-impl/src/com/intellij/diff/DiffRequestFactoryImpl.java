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

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffRequestFactoryImpl extends DiffRequestFactory {
  private DiffContentFactory myContentFactory = DiffContentFactory.getInstance();

  @Override
  @NotNull
  public ContentDiffRequest createFromFiles(@Nullable Project project, @NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    DiffContent content1 = myContentFactory.create(project, file1);
    DiffContent content2 = myContentFactory.create(project, file2);

    String title1 = getContentTitle(file1);
    String title2 = getContentTitle(file2);

    String title = getTitle(file1, file2);

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }

  @Override
  @NotNull
  public ContentDiffRequest createClipboardVsValue(@NotNull String value) {
    DiffContent content1 = myContentFactory.createClipboardContent();
    DiffContent content2 = myContentFactory.create(value);

    String title1 = DiffBundle.message("diff.content.clipboard.content.title");
    String title2 = DiffBundle.message("diff.content.selected.value");

    String title = DiffBundle.message("diff.clipboard.vs.value.dialog.title");

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }

  @Override
  @NotNull
  public String getContentTitle(@NotNull VirtualFile file) {
    if (file.isDirectory()) return file.getPath();

    VirtualFile parent = file.getParent();
    return getContentTitle(file.getName(), file.getPath(), parent != null ? parent.getPath() : null);
  }

  @Override
  @NotNull
  public String getTitle(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    if ((file1.isDirectory() || file2.isDirectory()) && file1.getPath().equals(file2.getPath())) return file1.getPath();
    if (file1.isDirectory() ^ file2.isDirectory()) return getContentTitle(file1) + " vs " + getContentTitle(file2);

    VirtualFile parent1 = file1.getParent();
    VirtualFile parent2 = file2.getParent();
    return getRequestTitle(file1.getName(), file1.getPath(), parent1 != null ? parent1.getPath() : null,
                           file2.getName(), file2.getPath(), parent2 != null ? parent2.getPath() : null,
                           " vs ");
  }

  @Override
  @NotNull
  public String getTitle(@NotNull VirtualFile file) {
    return getTitle(file, file);
  }

  @NotNull
  public static String getContentTitle(@NotNull FilePath path) {
    if (path.isDirectory()) return path.getPath();
    FilePath parent = path.getParentPath();
    return getContentTitle(path.getName(), path.getPath(), parent != null ? parent.getPath() : null);
  }

  @NotNull
  public static String getTitle(@NotNull FilePath path1, @NotNull FilePath path2, @NotNull String separator) {
    if ((path1.isDirectory() || path2.isDirectory()) && path1.getPath().equals(path2.getPath())) return path1.getPath();
    if (path1.isDirectory() ^ path2.isDirectory()) return getContentTitle(path1) + " vs " + getContentTitle(path2);

    FilePath parent1 = path1.getParentPath();
    FilePath parent2 = path2.getParentPath();
    return getRequestTitle(path1.getName(), path1.getPath(), parent1 != null ? parent1.getPath() : null,
                           path2.getName(), path2.getPath(), parent2 != null ? parent2.getPath() : null,
                           separator);
  }

  @NotNull
  private static String getContentTitle(@NotNull String name, @NotNull String path, @Nullable String parentPath) {
    if (parentPath != null) {
      return name + " (" + parentPath + ")";
    }
    else {
      return path;
    }
  }

  @NotNull
  private static String getRequestTitle(@NotNull String name1, @NotNull String path1, @Nullable String parentPath1,
                                        @NotNull String name2, @NotNull String path2, @Nullable String parentPath2,
                                        @NotNull String sep) {
    if (path1.equals(path2)) return getContentTitle(name1, path1, parentPath1);

    if (Comparing.equal(parentPath1, parentPath2)) {
      if (parentPath1 != null) {
        return name1 + sep + name2 + " (" + parentPath1 + ")";
      }
      else {
        return path1 + sep + path2;
      }
    }
    else {
      if (name1.equals(name2)) {
        if (parentPath1 != null && parentPath2 != null) {
          return name1 + " (" + parentPath1 + sep + parentPath2 + ")";
        }
        else {
          return path1 + sep + path2;
        }
      }
      else {
        if (parentPath1 != null && parentPath2 != null) {
          return name1 + sep + name2 + " (" + parentPath1 + sep + parentPath2 + ")";
        }
        else {
          return path1 + sep + path2;
        }
      }
    }
  }
}
