// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import java.util.List;

public interface BuilderOptions {
  BuilderOptions EMPTY = create(List.of(), List.of());
  
  List<String> getJavaOptions();
  
  List<String> getKotlinOptions();

  static BuilderOptions create(List<String> javaOpts, List<String> kotlinOpts) {
    List<String> forJava = List.copyOf(javaOpts);
    List<String> forKotlin = List.copyOf(kotlinOpts);
    return new BuilderOptions() {
      @Override
      public List<String> getJavaOptions() {
        return forJava;
      }

      @Override
      public List<String> getKotlinOptions() {
        return forKotlin;
      }
    };
  }
}
