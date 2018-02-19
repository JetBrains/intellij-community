// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MockApplicationEx extends MockApplication implements ApplicationEx {
  public MockApplicationEx(@NotNull Disposable parentDisposable) {
    super(parentDisposable);
  }

  @NotNull
  @Override
  public String getName() {
    return "mock";
  }

  @Override
  public boolean holdsReadLock() {
    return false;
  }

  @Override
  public void load(@Nullable String path) {
  }

  @Override
  public void load() {
    load(null);
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public void exit(boolean force, boolean exitConfirmed) {
  }

  @Override
  public void restart(boolean exitConfirmed) {
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process, @NotNull final String progressTitle, final boolean canBeCanceled, @Nullable final Project project,
                                                     final JComponent parentComponent) {
    return false;
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     JComponent parentComponent,
                                                     String cancelText) {
    return false;
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project) {
    return false;
  }

  @Override
  public boolean runProcessWithProgressSynchronouslyInReadAction(@Nullable Project project,
                                                                 @NotNull String progressTitle,
                                                                 boolean canBeCanceled,
                                                                 String cancelText,
                                                                 JComponent parentComponent,
                                                                 @NotNull Runnable process) {
    return false;
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  @Override
  public void assertIsDispatchThread(@Nullable final JComponent component) {
  }

  @Override
  public void assertTimeConsuming() {
  }

  @Override
  public boolean tryRunReadAction(@NotNull Runnable runnable) {
    runReadAction(runnable);
    return true;
  }

  @Override
  public boolean isWriteActionInProgress() {
    return false;
  }

  @Override
  public boolean isWriteActionPending() {
    return false;
  }

  @Override
  public boolean isSaveAllowed() {
    return true;
  }

  @Override
  public void setSaveAllowed(boolean value) {
  }
}
