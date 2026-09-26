// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

/**
 * Test-only crash points inside the build.
 * A test selects one point with the VM flag read by {@link VMFlags#getTestCrashPointName()}.
 * At the active point the worker stops at once via {@link Runtime#halt(int)}.
 * The halt skips the finally blocks and the shutdown hooks.
 * This models a build process that gets killed at that point.
 */
public enum CrashPoint {
  /** The initial round starts. The config state is not deleted yet. */
  BEFORE_BUILD_START,
  /** The round data is integrated into the dependency graph. At least one more round is pending. */
  AFTER_ROUND_INTEGRATE,
  /** The dependencies are backed up. The config state is not saved yet. */
  AFTER_DEPS_BACKUP,
  /** The storages are closed. The build state is not saved yet. */
  BEFORE_SAVE_STATE;

  private static final String ourActiveName = VMFlags.getTestCrashPointName();

  public void reached() {
    if (ourActiveName != null && ourActiveName.equalsIgnoreCase(name())) {
      System.err.println("Test crash point hit: " + name());
      Runtime.getRuntime().halt(73);
    }
  }
}
