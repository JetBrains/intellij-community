// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IdeaProjectLoaderUtil {
  private static final String JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME = "JPS_BOOTSTRAP_COMMUNITY_HOME";
  private static final String ULTIMATE_REPO_MARKER_FILE = ".ultimate.root.marker";
  private static final String COMMUNITY_REPO_MARKER_FILE = "intellij.idea.community.main.iml";

  /**
   * @param klass must be a class inside of idea home directory, the one with <code>file</code> protocol. Jar files in maven directory aren't accepted.
   */
  public static Path guessUltimateHome(Class<?> klass) {
    final Path start = getSomeRoot(klass);
    Path home = start;
    while (home != null) {
      if (Files.exists(home.resolve(ULTIMATE_REPO_MARKER_FILE))) {
        return home;
      }
      // Handle Rider layout case
      if (Files.exists(home.resolve("ultimate").resolve(ULTIMATE_REPO_MARKER_FILE))) {
        return home.resolve("ultimate");
      }

      home = home.getParent();
    }

    throw new IllegalArgumentException("Cannot guess ultimate project home from root '" + start + "'" +
                                       ", marker file '" + ULTIMATE_REPO_MARKER_FILE + "'");
  }

  /**
   * @param klass must be a class inside of idea home directory, the one with <code>file</code> protocol. Jar files in maven directory aren't accepted.
   */
  public static BuildDependenciesCommunityRoot guessCommunityHome(Class<?> klass) {
    final Path start = getSomeRoot(klass);
    Path home = start;

    while (home != null) {
      if (Files.exists(home.resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return new BuildDependenciesCommunityRoot(home);
      }

      if (Files.exists(home.resolve("community").resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return new BuildDependenciesCommunityRoot(home.resolve("community"));
      }

      // Handle Rider layout case
      if (Files.exists(home.resolve("ultimate").resolve("community").resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return new BuildDependenciesCommunityRoot(home.resolve("ultimate").resolve("community"));
      }

      home = home.getParent();
    }

    throw new IllegalArgumentException("Cannot guess community project home from root '" + start + "'" +
                                       ", marker file '" + COMMUNITY_REPO_MARKER_FILE + "'");
  }

  private static Path getSomeRoot(Class<?> klass) {
    // Under jps-bootstrap home is already known, reuse it
    String communityHome = System.getenv(JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME);
    if (communityHome != null) {
      return Path.of(communityHome).normalize();
    }

    var path = getPathFromClass(klass);
    if (!path.toString().endsWith("class")) {
      throw new IllegalArgumentException(
        String.format("To guess idea home, you must provide class that resides in .class file inside of idea home dir. " +
                      "But provided %s resides in %s", klass, path));
    }
    return path;
  }

  private static Path getPathFromClass(Class<?> klass) {
    final URL classFileURL = klass.getResource(klass.getSimpleName() + ".class");
    if (classFileURL == null) {
      throw new IllegalStateException("Could not get .class file location from class " + klass.getName());
    }
    return Path.of(UrlClassLoader.urlToFilePath(classFileURL.getPath()));
  }
}
