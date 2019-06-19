// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
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

public class PathRelativizerService {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.jps.incremental.relativizer.PathRelativizerService");

  private List<PathRelativizer> myRelativizers;
  private Set<String> myUnhandledPaths;

  public PathRelativizerService(@Nullable String projectPath, @Nullable String buildDirPath) {
    initialize(projectPath, buildDirPath, null); //TODO :: fix null value for references
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
    myRelativizers = ContainerUtil.newSmartList(new ProjectPathRelativizer(projectPath),
                                                new JavaSdkPathRelativizer(javaSdks),
                                                new BuildDataPathRelativizer(buildDirPath),
                                                new MavenPathRelativizer());
    myUnhandledPaths = new LinkedHashSet<>();
  }

  @NotNull
  public String toRelative(@NotNull String path) {
    for (PathRelativizer relativizer : myRelativizers) {
      if (relativizer.isAcceptableAbsolutePath(path)) {
        return relativizer.toRelativePath(path);
      }
    }
    myUnhandledPaths.add(path);
    return path;
  }

  @NotNull
  public String toFull(@NotNull String path) {
    for (PathRelativizer relativizer : myRelativizers) {
      if (relativizer.isAcceptableRelativePath(path)) {
        return relativizer.toAbsolutePath(path);
      }
    }
    return path;
  }

  public void reportUnhandledPaths() { //TODO:: Rewrite this part
    if (!LOG.isDebugEnabled()) return;
    final StringBuilder logBuilder = new StringBuilder();
    myUnhandledPaths.forEach(it -> logBuilder.append(it).append("\n"));
    LOG.debug("Unhandled by relativizer paths:" + "\n" + logBuilder.toString());
    myUnhandledPaths = new LinkedHashSet<>();
  }
}
