// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.projectWizard.importSources.util.CommonSourceRootDetectionUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public abstract class JavaSourceRootDetector extends ProjectStructureDetector {
  private static final int THRESHOLD = 20;

  @Override
  public @NotNull DirectoryProcessingResult detectRoots(@NotNull File dir,
                                                        File @NotNull [] children,
                                                        @NotNull File base,
                                                        @NotNull List<DetectedProjectRoot> result) {
    final String fileExtension = getFileExtension();
    if (JavaFileType.DEFAULT_EXTENSION.equals(fileExtension)) {
      for (File child : children) {
        if (child.isFile() && "module-info.java".equals(child.getName())) {
          JavaModuleSourceRoot sourceRoot = new JavaModuleSourceRoot(dir, getLanguageName(), true);
          result.add(sourceRoot);
          return DirectoryProcessingResult.SKIP_CHILDREN;
        }
      }
    }

    for (File child : children) {
      if (child.isFile() && FileUtilRt.extensionEquals(child.getName(), fileExtension)) {
        Pair<File, String> root = CommonSourceRootDetectionUtil.IO_FILE.suggestRootForFileWithPackageStatement(
          child, base, getPackageNameFetcher(), true);
        if (root != null) {
          JavaModuleSourceRoot sourceRoot = new JavaModuleSourceRoot(root.getFirst(), root.getSecond(), getLanguageName());
          result.add(sourceRoot);
          // Sometimes, test data .java files have a package statement which includes names of all parent directories
          // (e.g. files in jdk/test/java/awt/regtesthelpers in JDK sources have the package `test.java.awt.regtesthelpers`).
          // This check allows us to not skip searching for other java source roots when the first such file is found.
          if (areLastRootsTheSame(result, sourceRoot)) {
            return DirectoryProcessingResult.skipChildrenAndParentsUpTo(root.getFirst());
          }
        }
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  private static boolean areLastRootsTheSame(List<DetectedProjectRoot> result, JavaModuleSourceRoot root) {
    if (result.size() < THRESHOLD) return false;

    List<DetectedProjectRoot> lastItems = result.subList(result.size() - THRESHOLD, result.size());
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

  protected abstract @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getLanguageName();

  protected abstract @NotNull String getFileExtension();

  protected abstract @NotNull NullableFunction<CharSequence, String> getPackageNameFetcher();
}
