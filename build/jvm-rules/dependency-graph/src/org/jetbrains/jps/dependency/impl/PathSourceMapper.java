// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

public final class PathSourceMapper implements NodeSourcePathMapper {
  private final @NotNull Function<String, String> toFull;
  private final @NotNull Function<String, String> toRelative;

  public PathSourceMapper() {
    this(Function.identity(), Function.identity());
  }
  
  public PathSourceMapper(@NotNull Function<String, String> toFull, @NotNull Function<String, String> toRelative) {
    this.toFull = toFull;
    this.toRelative = toRelative;
  }

  @Override
  public NodeSource toNodeSource(File file) {
    return toNodeSource(file.toPath());
  }

  @Override
  public NodeSource toNodeSource(Path path) {
    return toNodeSource(path.normalize().toString());
  }

  @Override
  public NodeSource toNodeSource(String path) {
    return new PathSource(toRelative.apply(path));
  }

  @Override
  public Path toPath(NodeSource nodeSource) {
    return Path.of(toFull.apply(nodeSource.toString()));
  }
  
}
