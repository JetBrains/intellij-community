package com.intellij.tools.build.bazel.jvmIncBuilder.runner;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 * Date: 31 May 2025
 */
public
interface OutputFile {

  enum Kind {
    bytecode, source, other
  }

  Kind getKind();

  @NotNull String getPath();

  byte @NotNull [] getContent();

  default boolean isFromGeneratedSource() {
    return false;
  }
}
