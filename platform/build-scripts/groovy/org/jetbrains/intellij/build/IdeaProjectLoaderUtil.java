// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build;

import com.intellij.util.io.URLUtil;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class IdeaProjectLoaderUtil {
  private static final String ULTIMATE_REPO_MARKER_FILE = "intellij.idea.ultimate.tests.main.iml";
  private static final String COMMUNITY_REPO_MARKER_FILE = "intellij.idea.community.main.iml";

  public static Path guessUltimateHome(Class<?> klass) {
    final Path start = getPathFromClass(klass);
    Path home = start;

    while (home != null) {
      if (Files.exists(home.resolve(ULTIMATE_REPO_MARKER_FILE))) {
        return home;
      }

      home = home.getParent();
    }

    throw new IllegalArgumentException("Cannot guess ultimate project home from class '" + klass.getName() + "'" +
                                       "(directory '" + start + "', marker file '" + ULTIMATE_REPO_MARKER_FILE + "')");
  }

  public static Path guessCommunityHome(Class<?> klass) {
    final Path start = getPathFromClass(klass);
    Path home = start;

    while (home != null) {
      if (Files.exists(home.resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return home;
      }

      if (Files.exists(home.resolve("community").resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return home.resolve("community");
      }

      home = home.getParent();
    }

    throw new IllegalArgumentException("Cannot guess community project home from class '" + klass.getName() + "'" +
                                       "(directory '" + start + "', marker file '" + COMMUNITY_REPO_MARKER_FILE + "')");
  }

  private static Path getPathFromClass(Class<?> klass) {
    final URL classFileURL = klass.getResource(klass.getSimpleName() + ".class");
    if (classFileURL == null) {
      throw new IllegalStateException("Could not get .class file location from class " + klass.getName());
    }

    return URLUtil.urlToFile(classFileURL).toPath().toAbsolutePath();
  }
}
