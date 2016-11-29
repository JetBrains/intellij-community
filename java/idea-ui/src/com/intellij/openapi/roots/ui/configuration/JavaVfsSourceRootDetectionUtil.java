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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaVfsSourceRootDetectionUtil {
  private JavaVfsSourceRootDetectionUtil() {}

  /**
   * Scan directory and detect java source roots within it. The source root is detected as the following:
   * <ol>
   * <li>It contains at least one Java file.</li>
   * <li>Java file is located in the sub-folder that matches package statement in the file.</li>
   * </ol>
   *
   * @param dir a directory to scan
   * @param progressIndicator
   * @return a list of found source roots within directory. If no source roots are found, a empty list is returned.
   */
  @NotNull
  public static List<VirtualFile> suggestRoots(@NotNull VirtualFile dir, @NotNull final ProgressIndicator progressIndicator) {
    if (!dir.isDirectory()) {
      return ContainerUtil.emptyList();
    }

    final FileTypeManager typeManager = FileTypeManager.getInstance();
    final ArrayList<VirtualFile> foundDirectories = new ArrayList<>();
    try {
      VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor() {
        @NotNull
        @Override
        public Result visitFileEx(@NotNull VirtualFile file) {
          progressIndicator.checkCanceled();

          if (file.isDirectory()) {
            if (typeManager.isFileIgnored(file) || StringUtil.startsWithIgnoreCase(file.getName(), "testData")) {
              return SKIP_CHILDREN;
            }
          }
          else {
            FileType type = typeManager.getFileTypeByFileName(file.getName());
            if (StdFileTypes.JAVA == type) {
              VirtualFile root = suggestRootForJavaFile(file);
              if (root != null) {
                foundDirectories.add(root);
                return skipTo(root);
              }
            }
          }

          return CONTINUE;
        }
      });
    }
    catch (ProcessCanceledException ignore) { }

    return foundDirectories;
  }

  @Nullable
  private static VirtualFile suggestRootForJavaFile(VirtualFile javaFile) {
    if (javaFile.isDirectory()) return null;

    CharSequence chars = LoadTextUtil.loadText(javaFile);

    String packageName = JavaSourceRootDetectionUtil.getPackageName(chars);
    if (packageName != null){
      VirtualFile root = javaFile.getParent();
      int index = packageName.length();
      while(index > 0){
        int index1 = packageName.lastIndexOf('.', index - 1);
        String token = packageName.substring(index1 + 1, index);
        String dirName = root.getName();
        final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
        if (!equalsToToken) {
          return null;
        }
        root = root.getParent();
        if (root == null){
          return null;
        }
        index = index1;
      }
      return root;
    }

    return null;
  }
}
