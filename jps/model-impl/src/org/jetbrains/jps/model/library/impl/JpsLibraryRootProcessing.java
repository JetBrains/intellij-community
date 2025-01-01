// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class JpsLibraryRootProcessing {
  private static final Set<String> AR_EXTENSIONS = CollectionFactory.createFilePathSet(Arrays.asList("jar", "zip", "swc", "ane"));

  private JpsLibraryRootProcessing() {
  }

  public static @NotNull List<String> convertToUrls(@NotNull List<JpsLibraryRoot> roots) {
    List<String> urls = new ArrayList<>();
    for (JpsLibraryRoot root : roots) {
      switch (root.getInclusionOptions()) {
        case ROOT_ITSELF:
          urls.add(root.getUrl());
          break;
        case ARCHIVES_UNDER_ROOT:
          collectArchives(JpsPathUtil.urlToFile(root.getUrl()), false, urls);
          break;
        case ARCHIVES_UNDER_ROOT_RECURSIVELY:
          collectArchives(JpsPathUtil.urlToFile(root.getUrl()), true, urls);
          break;
      }
    }
    return urls;
  }

  public static @NotNull List<File> convertToFiles(@NotNull List<JpsLibraryRoot> roots) {
    List<String> urls = convertToUrls(roots);
    List<File> files = new ArrayList<>(urls.size());
    for (String url : urls) {
      if (!JpsPathUtil.isJrtUrl(url)) {
        files.add(JpsPathUtil.urlToFile(url));
      }
    }
    return files;
  }

  public static @NotNull List<Path> convertToPaths(@NotNull List<JpsLibraryRoot> roots) {
    List<String> urls = convertToUrls(roots);
    List<Path> result = new ArrayList<>(urls.size());
    for (String url : urls) {
      if (!JpsPathUtil.isJrtUrl(url)) {
        result.add(Path.of(JpsPathUtil.urlToPath(url)));
      }
    }
    return result;
  }

  private static void collectArchives(File file, boolean recursively, List<? super String> result) {
    final File[] children = file.listFiles();
    if (children != null) {
      // There is no guarantee about order of files on different OS
      Arrays.sort(children);
      for (File child : children) {
        final String extension = FileUtilRt.getExtension(child.getName());
        if (child.isDirectory()) {
          if (recursively) {
            collectArchives(child, recursively, result);
          }
        }
        // todo get list of extensions mapped to Archive file type from IDE settings
        else if (AR_EXTENSIONS.contains(extension)) {
          result.add(JpsPathUtil.getLibraryRootUrl(child));
        }
      }
    }
  }
}
