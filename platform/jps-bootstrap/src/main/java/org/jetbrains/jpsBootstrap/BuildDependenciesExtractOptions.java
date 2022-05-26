// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap;

public enum BuildDependenciesExtractOptions {
  /**
   * Strip leading component from file names on extraction.
   * Asserts that the leading component is the same for every file
   */
  STRIP_ROOT,
}
