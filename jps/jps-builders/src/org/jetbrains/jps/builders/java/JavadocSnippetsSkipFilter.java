// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.FileFilter;
import java.util.function.BiFunction;

@ApiStatus.Internal
public final class JavadocSnippetsSkipFilter implements FileFilter {
  public static final String SNIPPETS_FOLDER = "snippet-files";
  
  private static final String SNIPPETS_FOLDER_PATTERN = "/" + SNIPPETS_FOLDER + "/";
  static final BiFunction<String, Integer, Integer> FIND_SNIPPETS_FOLDER_PATTERN = SystemInfoRt.isFileSystemCaseSensitive?
    (s, from) -> Strings.indexOf(s, SNIPPETS_FOLDER_PATTERN, from):
    (s, from) -> Strings.indexOfIgnoreCase(s, SNIPPETS_FOLDER_PATTERN, from);

  private final File myRoot;

  JavadocSnippetsSkipFilter(File root) {
    myRoot = root;
  }

  @Override
  public boolean accept(File file) {
    return !isJavadocExtSnippet(file);
  }

  private boolean isJavadocExtSnippet(File file) {
    String filePath = FileUtilRt.toSystemIndependentName(file.getPath());
    int patternIndex = FIND_SNIPPETS_FOLDER_PATTERN.apply(filePath, 0);
    if (patternIndex < 0) {
      return false;
    }
    // ensure the file is under the root
    String rootPath = FileUtilRt.toSystemIndependentName(myRoot.getPath());
    if (!FileUtil.startsWith(filePath, rootPath, SystemInfoRt.isFileSystemCaseSensitive, true)) {
      return false;
    }
    int fromIndex = rootPath.endsWith("/")? rootPath.length() - 1 : rootPath.length();
    return patternIndex >= fromIndex || FIND_SNIPPETS_FOLDER_PATTERN.apply(filePath, fromIndex) >= 0;
  }

}
