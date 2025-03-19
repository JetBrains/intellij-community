// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class IdeaProjectLoaderUtil {
  private static final String JPS_BOOTSTRAP_COMMUNITY_HOME_ENV_NAME = "JPS_BOOTSTRAP_COMMUNITY_HOME";
  private static final String ULTIMATE_REPO_MARKER_FILE = ".ultimate.root.marker";
  private static final String COMMUNITY_REPO_MARKER_FILE = "intellij.idea.community.main.iml";
  private static final String INTELLIJ_BUILD_COMMUNITY_HOME_PATH = "intellij.build.community.home.path";
  private static final String INTELLIJ_BUILD_ULTIMATE_HOME_PATH = "intellij.build.ultimate.home.path";

  /**
   * This method only for internal usage. Instead of use: org.jetbrains.intellij.build.BuildPaths.Companion#getULTIMATE_HOME().
   * @param klass must be a class inside idea home directory, the one with <code>file</code> protocol. Jar files in maven directory aren't accepted.
   */
  @ApiStatus.Internal
  public static Path guessUltimateHome(Class<?> klass) {
    String ultimateHomePathOverride = System.getProperty(INTELLIJ_BUILD_ULTIMATE_HOME_PATH);
    if (ultimateHomePathOverride != null) {
      Path path = Paths.get(ultimateHomePathOverride);
      if (!path.toFile().exists()) {
        throw new IllegalArgumentException("Ultimate home path: '" + path
                                           + "' passed via system property: '" + INTELLIJ_BUILD_ULTIMATE_HOME_PATH + " not exists");
      }
      return path;
    }
    final Path start = getSomeRoot(klass);
    Path home = start;
    while (home != null) {
      if (Files.exists(home.resolve(ULTIMATE_REPO_MARKER_FILE))) {
        return home;
      }
      home = home.getParent();
    }

    throw new IllegalArgumentException("Cannot guess ultimate project home from root '" + start + "'" +
                                       ", marker file '" + ULTIMATE_REPO_MARKER_FILE + "'");
  }

  /**
   * This method only for internal usage. Instead of use: org.jetbrains.intellij.build.BuildPaths.Companion#getCOMMUNITY_ROOT().
   * @param klass must be a class inside idea home directory, the one with <code>file</code> protocol. Jar files in maven directory aren't accepted.
   */
  @ApiStatus.Internal
  public static BuildDependenciesCommunityRoot guessCommunityHome(Class<?> klass) {
    String communityHomePathOverride = System.getProperty(INTELLIJ_BUILD_COMMUNITY_HOME_PATH);
    if (communityHomePathOverride != null) {
      Path path = Paths.get(communityHomePathOverride);
      if (!path.toFile().exists()) {
        throw new IllegalArgumentException("Community home path: '" + path
                                           + "' passed via system property: '" + INTELLIJ_BUILD_COMMUNITY_HOME_PATH + " not exists");
      }
      return new BuildDependenciesCommunityRoot(path);
    }
    final Path start = getSomeRoot(klass);
    Path home = start;

    while (home != null) {
      if (Files.exists(home.resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return new BuildDependenciesCommunityRoot(home);
      }

      if (Files.exists(home.resolve("community").resolve(COMMUNITY_REPO_MARKER_FILE))) {
        return new BuildDependenciesCommunityRoot(home.resolve("community"));
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
      String ideaHomePath = System.getProperty("idea.home.path");
      if (ideaHomePath != null) {
        return Path.of(ideaHomePath);
      }
      throw new IllegalArgumentException(
        String.format("To guess idea home, you must provide class that resides in .class file inside of idea home dir. " +
                      "But provided %s resides in %s", klass, path));
    }
    return path;
  }

  private static Path getPathFromClass(Class<?> klass) {
    String klassFileName = klass.getName().replace(klass.getPackageName() + ".", "");
    final URL classFileURL = klass.getResource(klassFileName + ".class");
    if (classFileURL == null) {
      throw new IllegalStateException("Could not get .class file location from class " + klass.getName());
    }
    return Path.of(UrlClassLoader.urlToFilePath(classFileURL.getPath()));
  }
}
