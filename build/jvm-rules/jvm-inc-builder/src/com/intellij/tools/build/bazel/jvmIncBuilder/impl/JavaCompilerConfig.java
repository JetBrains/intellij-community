package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

/**
 *  Configuration for javac command line building
 *  todo: Some of these flags can be exposed in corresponding bazel rules
 */
public interface JavaCompilerConfig {
  boolean USE_RELEASE_OPTION = false;
  boolean GENERATE_DEBUG_INFO = true;
  boolean REPORT_DEPRECATION = true;
}
