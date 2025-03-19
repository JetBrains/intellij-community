// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.HashMapZipFile;
import com.intellij.util.lang.ImmutableZipEntry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UrlUtil {
  private static final String URL_PATH_SEPARATOR = "/";
  private static final String FILE_PROTOCOL_PREFIX = URLUtil.FILE_PROTOCOL + ":";

  public static @NotNull String loadText(@NotNull URL url) throws IOException {
    return ResourceUtil.loadText(url.openStream());
  }

  public static @NotNull List<String> getChildrenRelativePaths(@NotNull URL root) throws IOException {
    String protocol = root.getProtocol();
    if ("jar".equalsIgnoreCase(protocol)) {
      return getChildPathsFromJar(root);
    }
    else if (URLUtil.FILE_PROTOCOL.equalsIgnoreCase(protocol)) {
      return getChildPathsFromFile(root);
    }
    else {
      return Collections.emptyList();
    }
  }

  private static @NotNull List<String> getChildPathsFromFile(@NotNull URL root) {
    final List<String> paths = new ArrayList<>();
    final File rootFile = new File(FileUtil.unquote(root.getPath()));
    new Object() {
      private void collectFiles(File fromFile, String prefix) {
        File[] list = fromFile.listFiles();
        if (list == null) {
          return;
        }

        for (File file : list) {
          String childRelativePath = prefix.isEmpty() ? file.getName() : prefix + URL_PATH_SEPARATOR + file.getName();
          if (file.isDirectory()) {
            collectFiles(file, childRelativePath);
          }
          else {
            paths.add(childRelativePath);
          }
        }
      }
    }.collectFiles(rootFile, "");
    return paths;
  }

  static @NotNull List<String> getChildPathsFromJar(@NotNull URL root) throws IOException {
    // Parse e.g. the following url: jar:file:/C:/build/idea-sandbox/plugins/lib/jar-2022.1.9999%20-%20Copy.jar!/file Templates/
    if (!URLUtil.JAR_PROTOCOL.equalsIgnoreCase(root.getProtocol())) {
      throw new RuntimeException("This function accepts only jar urls: " + root);
    }

    String rootPath = root.getPath();
    int jarSeparatorIndex = rootPath.indexOf(URLUtil.JAR_SEPARATOR);
    if (jarSeparatorIndex <= 0) {
      throw new RuntimeException("This function accepts only urls with '" + URLUtil.JAR_SEPARATOR + "': " + root);
    }

    String rootDirName = rootPath.substring(jarSeparatorIndex + 2);
    if (!rootDirName.endsWith(URL_PATH_SEPARATOR)) {
      rootDirName += URL_PATH_SEPARATOR;
    }

    String fileUrl = rootPath.substring(0, jarSeparatorIndex);
    if (!fileUrl.startsWith("file:")) {
      throw new RuntimeException("This function accepts 'jar:file:' urls: " + root);
    }

    File file = URLUtil.urlToFile(new URL(fileUrl));

    List<String> paths = new ArrayList<>();
    try (HashMapZipFile zipFile = HashMapZipFile.load(file.toPath())) {
      for (ImmutableZipEntry entry : zipFile.getEntries()) {
        String path = entry.getName();
        if (path.startsWith(rootDirName)) {
          paths.add(path.substring(rootDirName.length()));
        }
      }
      return paths;
    }
    catch (IOException | RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
