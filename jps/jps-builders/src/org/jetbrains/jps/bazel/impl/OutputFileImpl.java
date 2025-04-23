// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.runner.OutputSink;

public class OutputFileImpl implements OutputSink.OutputFile {
  private static final byte[] EMPTY_CONTENT = new byte[0];
  private final String myPath;
  private final Kind myKind;
  private final byte[] myContent;
  private final boolean myFromGeneratedSource;

  public OutputFileImpl(@NotNull String path, @NotNull Kind kind, byte @NotNull [] content) {
    this(path, kind, content, false);
  }

  public OutputFileImpl(@NotNull String path, @NotNull Kind kind, byte @NotNull [] content, boolean fromGeneratedSource) {
    myPath = path;
    myKind = kind;
    myContent = content.length == 0? EMPTY_CONTENT : content;
    myFromGeneratedSource = fromGeneratedSource;
  }

  @Override
  public Kind getKind() {
    return myKind;
  }

  @Override
  public @NotNull String getPath() {
    return myPath;
  }

  @Override
  public byte @NotNull [] getContent() {
    return myContent;
  }

  @Override
  public boolean isFromGeneratedSource() {
    return myFromGeneratedSource;
  }
}
