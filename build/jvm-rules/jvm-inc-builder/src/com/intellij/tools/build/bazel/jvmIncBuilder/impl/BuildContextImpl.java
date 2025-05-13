// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;

import java.nio.file.Path;

public class BuildContextImpl implements BuildContext {
  private final String myTargetName;
  private final Path myBaseDir;
  private final PathSourceMapper myPathMapper;
  @NotNull
  private final Path myOutJar;
  @Nullable
  private final Path myAbiJar;
  private final Path myDataDir;

  public BuildContextImpl(String targetName, Path baseDir, Path outJar, @Nullable Path abiJar, String cachePrefix) {
    myTargetName = targetName;
    myBaseDir = baseDir;
    myPathMapper = new PathSourceMapper(
      relPath -> {
        Path abs = baseDir.resolve(Path.of(relPath)).normalize();
        return abs.toString().replace(baseDir.getFileSystem().getSeparator(), "/");
      },
      absPath -> {
        Path relative = baseDir.relativize(Path.of(absPath)).normalize();
        return relative.toString().replace(baseDir.getFileSystem().getSeparator(), "/");
      }
    );
    myOutJar = outJar;
    myAbiJar = abiJar;
    myDataDir = outJar.resolveSibling(cachePrefix + truncateExtension(outJar.getFileName().toString()) + "-ic");
  }

  @Override
  public String getTargetName() {
    return myTargetName;
  }

  @Override
  public boolean isRebuild() {
    return false; // todo
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public @NotNull Path getBaseDir() {
    return myBaseDir;
  }

  @Override
  public @NotNull Path getDataDir() {
    return myDataDir;
  }

  @Override
  public @NotNull Path getOutputZip() {
    return myOutJar;
  }

  @Override
  public @Nullable Path getAbiOutputZip() {
    return myAbiJar;
  }

  @Override
  public NodeSourceSnapshot getSources() {
    return null; // todo
  }

  @Override
  public NodeSourceSnapshot getBinaryDependencies() {
    return null; // todo
  }

  @Override
  public BuilderArgs getBuilderArgs() {
    return null; // todo
  }

  @Override
  public NodeSourcePathMapper getPathMapper() {
    return myPathMapper;
  }

  @Override
  public BuildProcessLogger getBuildLogger() {
    return BuildProcessLogger.EMPTY; // used for tests
  }

  @Override
  public void report(Message msg) {
    // todo
  }

  @Override
  public boolean hasErrors() {
    return false;
  }

  private static String truncateExtension(String filename) {
    int idx = filename.lastIndexOf('.');
    return idx >= 0? filename.substring(0, idx) : filename;
  }
}
