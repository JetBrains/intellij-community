/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.AnyModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class MockApplication extends MockComponentManager implements Application {
  public static int INSTANCES_CREATED = 0;

  public MockApplication(@NotNull Disposable parentDisposable) {
    super(null, parentDisposable);
    INSTANCES_CREATED++;
  }

  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public boolean isEAP() {
    return false;
  }

  @Override
  public boolean isDispatchThread() {
    return true;
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public void assertReadAccessAllowed() {
  }

  @Override
  public void assertWriteAccessAllowed() {
  }

  @Override
  public void assertIsDispatchThread() {
  }

  @Override
  public boolean isReadAccessAllowed() {
    return true;
  }

  @Override
  public boolean isWriteAccessAllowed() {
    return true;
  }

  @Override
  public boolean isUnitTestMode() {
    return true;
  }

  @Override
  public boolean isHeadlessEnvironment() {
    return true;
  }

  @Override
  public boolean isCommandLine() {
    return true;
  }

  @NotNull
  @Override
  public Future<?> executeOnPooledThread(@NotNull Runnable action) {
    return PooledThreadExecutor.INSTANCE.submit(action);
  }

  @NotNull
  @Override
  public <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action) {
    return PooledThreadExecutor.INSTANCE.submit(action);
  }

  @Override
  public boolean isDisposeInProgress() {
    return false;
  }

  @Override
  public boolean isRestartCapable() {
    return false;
  }

  @Override
  public void restart() {
  }

  @Override
  public void runReadAction(@NotNull Runnable action) {
    action.run();
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computation) {
    return computation.compute();
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return computation.compute();
  }

  @Override
  public void runWriteAction(@NotNull Runnable action) {
    action.run();
  }

  @Override
  public <T> T runWriteAction(@NotNull Computable<T> computation) {
    return computation.compute();
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return computation.compute();
  }

  @NotNull
  @Override
  public AccessToken acquireReadActionLock() {
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }

  @NotNull
  @Override
  public AccessToken acquireWriteActionLock(@Nullable Class marker) {
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }

  @Override
  public boolean hasWriteAction(@NotNull Class<?> actionClass) {
    return false;
  }

  @Override
  public void addApplicationListener(@NotNull ApplicationListener listener) {
  }

  @Override
  public void addApplicationListener(@NotNull ApplicationListener listener, @NotNull Disposable parent) {
  }

  @Override
  public void removeApplicationListener(@NotNull ApplicationListener listener) {
  }

  @Override
  public long getStartTime() {
    return 0;
  }

  @Override
  public long getIdleTime() {
    return 0;
  }

  @NotNull
  @Override
  public ModalityState getNoneModalityState() {
    return ModalityState.NON_MODAL;
  }

  @Override
  public void invokeLater(@NotNull final Runnable runnable, @NotNull final Condition expired) {
  }

  @Override
  public void invokeLater(@NotNull final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable) {
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
  }

  @Override
  @NotNull
  public ModalityInvokator getInvokator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException {
    invokeAndWait(runnable, getDefaultModalityState());
  }

  @NotNull
  @Override
  public ModalityState getCurrentModalityState() {
    return getNoneModalityState();
  }

  @NotNull
  @Override
  public ModalityState getAnyModalityState() {
    return AnyModalityState.ANY;
  }

  @NotNull
  @Override
  public ModalityState getModalityStateForComponent(@NotNull Component c) {
    return getNoneModalityState();
  }

  @NotNull
  @Override
  public ModalityState getDefaultModalityState() {
    return getNoneModalityState();
  }

  @Override
  public void exit() {
  }

  @Override
  public void saveAll() {
  }

  @Override
  public void saveSettings() {
  }
}
