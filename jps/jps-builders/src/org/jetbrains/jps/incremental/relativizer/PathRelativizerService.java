// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class PathRelativizerService {
  private static final Logger LOG = Logger.getInstance(PathRelativizerService.class);

  private static final String PROJECT_DIR_IDENTIFIER = "$PROJECT_DIR$";
  private static final String BUILD_DIR_IDENTIFIER = "$BUILD_DIR$";

  private final List<PathRelativizer> relativizers;
  private final Set<String> unhandledPaths = Collections.synchronizedSet(new LinkedHashSet<>());

  public PathRelativizerService(@Nullable String projectPath) {
    relativizers = initialize(projectPath, null, null, null);
  }

  public PathRelativizerService(@Nullable String projectPath, @Nullable Boolean projectDirIsCaseSensitive) {
    relativizers = initialize(projectPath, null, projectDirIsCaseSensitive, null);
  }

  public PathRelativizerService(@NotNull JpsProject project) {
    this(project, null);
  }

  public PathRelativizerService(@NotNull JpsProject project, @Nullable Boolean projectDirIsCaseSensitive) {
    Set<JpsSdk<?>> javaSdks = project.getModules().stream()
      .map(module -> module.getSdk(JpsJavaSdkType.INSTANCE))
      .filter(sdk -> sdk != null && sdk.getVersionString() != null && sdk.getHomePath() != null)
      .collect(Collectors.toSet());

    File projectBaseDirectory = JpsModelSerializationDataService.getBaseDirectory(project);
    relativizers = initialize(projectBaseDirectory == null ? null : projectBaseDirectory.getAbsolutePath(),
                              getBuildDirPath(project),
                              projectDirIsCaseSensitive,
                              javaSdks);
  }

  @TestOnly
  public PathRelativizerService() {
    relativizers = initialize(null, null, null, null);
  }

  public @NotNull String toRelative(@NotNull Path path) {
    return toRelative(path.toString());
  }

  /**
   * @param path absolute path which should be converted.
   *             It may use forward or backward slashes as separators, so there is no need to convert it before passing to the method
   * @return system-independent relative path
   */
  public @NotNull String toRelative(@NotNull String path) {
    String systemIndependentPath = FileUtilRt.toSystemIndependentName(path);
    for (PathRelativizer relativizer : relativizers) {
      String relativePath = relativizer.toRelativePath(systemIndependentPath);
      if (relativePath != null) {
        return relativePath;
      }
    }
    if (LOG.isDebugEnabled()) {
      unhandledPaths.add(path);
    }
    return systemIndependentPath;
  }

  /**
   * @param path relative path which should be converted.
   *             It may use forward or backward slashes as separators, so there is no need to convert it before passing to the method
   * @return system-independent absolute path
   */
  public @NotNull String toFull(@NotNull String path) {
    String systemIndependentPath = FileUtilRt.toSystemIndependentName(path);
    String fullPath;
    for (PathRelativizer relativizer : relativizers) {
      fullPath = relativizer.toAbsolutePath(systemIndependentPath);
      if (fullPath != null) {
        return fullPath;
      }
    }
    return systemIndependentPath;
  }

  public void reportUnhandledPaths() {
    if (LOG.isDebugEnabled()) {
      StringBuilder logBuilder = new StringBuilder();
      unhandledPaths.forEach(it -> logBuilder.append(it).append("\n"));
      LOG.debug("Unhandled by relativizer paths:" + "\n" + logBuilder);
      unhandledPaths.clear();
    }
  }

  private static @NotNull List<PathRelativizer> initialize(@Nullable String projectPath,
                                                           @Nullable String buildDirPath,
                                                           @Nullable Boolean projectDirIsCaseSensitive,
                                                           @Nullable Set<? extends JpsSdk<?>> javaSdks) {
    String normalizedProjectPath = projectPath == null ? null : normalizePath(projectPath);
    String normalizedBuildDirPath = buildDirPath == null ? null : normalizePath(buildDirPath);
    return List.of(
      new CommonPathRelativizer(normalizedBuildDirPath, BUILD_DIR_IDENTIFIER, projectDirIsCaseSensitive),
      new CommonPathRelativizer(normalizedProjectPath, PROJECT_DIR_IDENTIFIER),
      new JavaSdkPathRelativizer(javaSdks),
      new MavenPathRelativizer(),
      new GradlePathRelativizer()
    );
  }

  static @NotNull String normalizePath(@NotNull String path) {
    return StringUtil.trimTrailing(FileUtilRt.toSystemIndependentName(path), '/');
  }

  private static @Nullable String getBuildDirPath(@NotNull JpsProject project) {
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(project);
    if (projectExtension == null) {
      return null;
    }

    String url = projectExtension.getOutputUrl();
    if (url == null || url.isEmpty()) {
      return null;
    }
    return JpsPathUtil.urlToFile(url).getAbsolutePath();
  }
}
