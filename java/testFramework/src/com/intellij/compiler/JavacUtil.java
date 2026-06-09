// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Provides a {@link JavaCompiler} for tests that need to compile Java sources on the fly.
 */
public final class JavacUtil {

  private JavacUtil() { }

  /**
   * Returns the system Java compiler, falling back to instantiating {@code JavacTool} directly.
   * @throws IllegalStateException if no Java compiler is available (e.g. running on a JRE rather than a JDK)
   */
  public static JavaCompiler getJavac() {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler != null) {
      return compiler;
    }
    try {
      return (JavaCompiler)Class.forName("com.sun.tools.javac.api.JavacTool").getMethod("create").invoke(null);
    }
    catch (Exception e) {
      throw new IllegalStateException("No Java compiler available; tests must run on a JDK, not a JRE", e);
    }
  }
}
