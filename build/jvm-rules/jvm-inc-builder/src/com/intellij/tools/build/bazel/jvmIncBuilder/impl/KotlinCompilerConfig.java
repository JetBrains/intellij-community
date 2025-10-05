package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

/**
 *  Configuration for kotlinc command line building
 *  todo: Some of these flags can be exposed in corresponding bazel rules
 */
public interface KotlinCompilerConfig {
  boolean ENABLE_INCREMENTAL_COMPILATION = true;
  boolean INCLUDE_REFLECTION = false;
  boolean INCLUDE_STDLIB = false;
}
