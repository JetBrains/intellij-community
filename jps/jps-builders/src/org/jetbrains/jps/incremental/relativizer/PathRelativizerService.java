// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;

public class PathRelativizerService {
  private static final Logger LOG = Logger.getInstance(PathRelativizerService.class);

  private static final String PROJECT_DIR_IDENTIFIER = "$PROJECT_DIR$";
  private static final String BUILD_DIR_IDENTIFIER = "$BUILD_DIR$";

  private List<PathRelativizer> myRelativizers;
  private Set<String> myUnhandledPaths;

  public PathRelativizerService(@Nullable String projectPath, @Nullable String buildDirPath) {
    initialize(projectPath, buildDirPath, null);
  }

  public PathRelativizerService(@NotNull JpsProject project, @NotNull File buildDir) {
    File projectBaseDirectory = JpsModelSerializationDataService.getBaseDirectory(project);
    Set<JpsSdk<?>> javaSdks = project.getModules().stream().map(module -> module.getSdk(JpsJavaSdkType.INSTANCE))
      .filter(sdk -> sdk != null && sdk.getVersionString() != null && sdk.getHomePath() != null)
      .collect(Collectors.toSet());

    initialize(projectBaseDirectory != null ? projectBaseDirectory.getAbsolutePath() : null, buildDir.getPath(), javaSdks);
  }

  @TestOnly
  public PathRelativizerService() {
    initialize(null, null, null);
  }

  private void initialize(@Nullable String projectPath, @Nullable String buildDirPath, @Nullable Set<JpsSdk<?>> javaSdks) {
    String normalizedProjectPath = projectPath != null ? normalizePath(projectPath) : null;
    String normalizedBuildDirPath = buildDirPath != null ? normalizePath(buildDirPath) : null;
    myRelativizers =
      new SmartList<>(new CommonPathRelativizer(normalizedProjectPath, PROJECT_DIR_IDENTIFIER), new JavaSdkPathRelativizer(javaSdks),
        new CommonPathRelativizer(normalizedBuildDirPath, BUILD_DIR_IDENTIFIER), new MavenPathRelativizer());
    myUnhandledPaths = new LinkedHashSet<>();
  }

  /**
   * @param path absolute path which should be converted. It may use forward or backward slashes as separators
   *             so there is no need to convert it before passing to the method
   * @return system-independent relative path
   */
  @NotNull
  public String toRelative(@NotNull String path) {
    String systemIndependentPath = toSystemIndependentName(path);
    String relativePath;
    for (PathRelativizer relativizer : myRelativizers) {
      relativePath = relativizer.toRelativePath(systemIndependentPath);
      if (relativePath != null) return relativePath;
    }
    myUnhandledPaths.add(path);
    return systemIndependentPath;
  }

  /**
   * @param path relative path which should be converted. It may use forward or backward slashes as separators
   *             so there is no need to convert it before passing to the method
   * @return system-independent absolute path
   */
  @NotNull
  public String toFull(@NotNull String path) {
    String systemIndependentPath = toSystemIndependentName(path);
    String fullPath;
    for (PathRelativizer relativizer : myRelativizers) {
      fullPath = relativizer.toAbsolutePath(systemIndependentPath);
      if (fullPath != null) return fullPath;
    }
    return systemIndependentPath;
  }

  public void reportUnhandledPaths() {
    if (!LOG.isDebugEnabled()) return;
    final StringBuilder logBuilder = new StringBuilder();
    myUnhandledPaths.forEach(it -> logBuilder.append(it).append("\n"));
    LOG.debug("Unhandled by relativizer paths:" + "\n" + logBuilder.toString());
    myUnhandledPaths = new LinkedHashSet<>();
  }

  @NotNull
  static String normalizePath(@NotNull String path) {
    return StringUtil.trimTrailing(toSystemIndependentName(path), '/');
  }
}
