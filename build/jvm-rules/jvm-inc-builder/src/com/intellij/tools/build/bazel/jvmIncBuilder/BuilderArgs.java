// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import java.util.List;

public interface BuilderArgs {
  BuilderArgs EMPTY = new BuilderArgs() {
    @Override
    public List<String> getJavaCompilerArgs() {
      return List.of();
    }

    @Override
    public List<String> getKotlinCompilerArgs() {
      return List.of();
    }
  };
  
  List<String> getJavaCompilerArgs();
  
  List<String> getKotlinCompilerArgs();
}
