// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface ProjectEx extends Project {
  String NAME_FILE = ".name";

  void setProjectName(@NotNull String name);

  @TestOnly
  default boolean isLight() {
    return false;
  }

  /**
   * {@link Disposable} that will be disposed right after container started to be disposed.
   * Use it to dispose something that need to be disposed very early, e.g. {@link com.intellij.util.Alarm}.
   * Or, only and only in unit test mode, if you need to publish something to message bus during dispose.<p/>
   *
   * In unit test mode light project is not disposed, but this disposable is disposed for each test.
   * So, you don't need to have another disposable and can use this one instead.<p/>
   *
   * Dependent {@link Disposable#dispose} may be called in any thread.
   * Implementation of {@link Disposable#dispose} must be self-contained and isolated (getting services is forbidden, publishing to message bus is allowed only in tests).
   */
  @NotNull
  @ApiStatus.Internal
  Disposable getEarlyDisposable();

  @TestOnly
  default @Nullable String getCreationTrace() {
    return null;
  }
}
