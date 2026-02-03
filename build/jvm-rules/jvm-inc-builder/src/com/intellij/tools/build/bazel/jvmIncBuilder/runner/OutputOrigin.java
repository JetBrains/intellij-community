package com.intellij.tools.build.bazel.jvmIncBuilder.runner;

import org.jetbrains.jps.dependency.NodeSource;

public
interface OutputOrigin {
  enum Kind {
    java, kotlin
  }

  Kind getKind();

  Iterable<NodeSource> getSources();

  static OutputOrigin create(Kind kind, Iterable<NodeSource> originSources) {
    return new OutputOrigin() {
      @Override
      public Kind getKind() {
        return kind;
      }

      @Override
      public Iterable<NodeSource> getSources() {
        return originSources;
      }
    };
  }
}
