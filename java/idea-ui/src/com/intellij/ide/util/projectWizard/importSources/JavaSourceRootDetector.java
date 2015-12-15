/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.util.projectWizard.importSources.util.CommonSourceRootDetectionUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public abstract class JavaSourceRootDetector extends ProjectStructureDetector {
  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir, @NotNull File[] children, @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    final String fileExtension = getFileExtension();
    for (File child : children) {
      if (child.isFile() && FileUtilRt.extensionEquals(child.getName(), fileExtension)) {
        Pair<File, String> root = CommonSourceRootDetectionUtil.IO_FILE.suggestRootForFileWithPackageStatement(child, base,
                                                                                                               getPackageNameFetcher(),
                                                                                                               true);
        if (root != null) {
          JavaModuleSourceRoot sourceRoot = new JavaModuleSourceRoot(root.getFirst(), root.getSecond(), getLanguageName());
          result.add(sourceRoot);
          // sometimes java files from test data have package statement which includes names of all parent directories
          // (e.g. files in jdk/test/java/awt/regtesthelpers in JDK sources have package 'test.java.awt.regtesthelpers')
          // This check allows us to not skip searching for other java source roots when first such java file is found.
          if (areLastRootsTheSame(result, sourceRoot, 20)) {
            return DirectoryProcessingResult.skipChildrenAndParentsUpTo(root.getFirst());
          }
        }
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  private static boolean areLastRootsTheSame(List<DetectedProjectRoot> result, JavaModuleSourceRoot root, int threshold) {
    if (result.size() < threshold) return false;

    List<DetectedProjectRoot> lastItems = result.subList(result.size() - threshold, result.size());
    for (DetectedProjectRoot item : lastItems) {
      if (!(item instanceof JavaModuleSourceRoot)) return false;
      JavaModuleSourceRoot oldRoot = (JavaModuleSourceRoot)item;
      if (!FileUtil.filesEqual(oldRoot.getDirectory(), root.getDirectory()) || !oldRoot.getPackagePrefix().equals(root.getPackagePrefix())
          || !oldRoot.getRootTypeName().equals(root.getRootTypeName())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getDetectorId() {
    return "Java";
  }

  @NotNull
  protected abstract String getLanguageName();

  @NotNull
  protected abstract String getFileExtension();

  @NotNull
  protected abstract NullableFunction<CharSequence, String> getPackageNameFetcher();
}
