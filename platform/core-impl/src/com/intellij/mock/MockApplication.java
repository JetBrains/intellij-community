// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.lang.MetaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.AnyModalityState;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.concurrency.AppExecutorUtil;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Modifier;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class MockApplication extends MockComponentManager implements ApplicationEx, ComponentManagerEx {
  public static int INSTANCES_CREATED;

  public MockApplication(@NotNull Disposable parentDisposable) {
    super(null, parentDisposable);

    INSTANCES_CREATED++;
    //noinspection TestOnlyProblems
    Extensions.setRootArea(getExtensionArea(), parentDisposable);
  }

  @TestOnly
  public static @NotNull MockApplication setUp(@NotNull Disposable parentDisposable) {
    MockApplication app = new MockApplication(parentDisposable);
    ApplicationManager.setApplication(app, parentDisposable);
    return app;
  }

  @Override
  public final @Nullable <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return doGetService(serviceClass, false);
  }

  @Override
  public final <T> T getService(@NotNull Class<T> serviceClass) {
    return doGetService(serviceClass, true);
  }

  private <T> T doGetService(@NotNull Class<T> serviceClass, boolean createIfNeeded) {
    T service = super.getService(serviceClass);
    if (service == null &&
        createIfNeeded &&
        Modifier.isFinal(serviceClass.getModifiers()) &&
        serviceClass.isAnnotationPresent(Service.class)) {
      //noinspection SynchronizeOnThis,SynchronizationOnLocalVariableOrMethodParameter
      synchronized (serviceClass) {
        service = super.getService(serviceClass);
        if (service != null) {
          return service;
        }

        getPicoContainer().registerComponentImplementation(serviceClass.getName(), serviceClass);
        return super.getService(serviceClass);
      }
    }
    return service;
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
  public CoroutineScope getCoroutineScope() {
    return GlobalScope.INSTANCE;
  }

  @Override
  public boolean isDispatchThread() {
    return SwingUtilities.isEventDispatchThread();
  }

  @Override
  public boolean isWriteIntentLockAcquired() {
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
  public void assertReadAccessNotAllowed() {
  }

  @Override
  public void assertIsDispatchThread() {
  }

  @Override
  public void assertIsNonDispatchThread() {
  }

  @Override
  public void assertWriteIntentLockAcquired() {
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

  @Override
  public @NotNull Future<?> executeOnPooledThread(@NotNull Runnable action) {
    return AppExecutorUtil.getAppExecutorService().submit(action);
  }

  @Override
  public @NotNull <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action) {
    return AppExecutorUtil.getAppExecutorService().submit(action);
  }

  @Override
  public boolean isRestartCapable() {
    return false;
  }

  @Override
  public void invokeLaterOnWriteThread(@NotNull Runnable action) {
    action.run();
  }

  @Override
  public void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal) {
    action.run();
  }

  @Override
  public void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal, @NotNull Condition<?> expired) {
    action.run();
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

  @Override
  public @NotNull AccessToken acquireReadActionLock() {
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }

  @Override
  public @NotNull AccessToken acquireWriteActionLock(@Nullable Class<?> marker) {
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

  @Override
  public @NotNull ModalityState getNoneModalityState() {
    return ModalityState.nonModal();
  }

  @Override
  public void invokeLater(final @NotNull Runnable runnable, final @NotNull Condition<?> expired) {
  }

  @Override
  public void invokeLater(final @NotNull Runnable runnable, final @NotNull ModalityState state, final @NotNull Condition<?> expired) {
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable) {
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    if (isDispatchThread()) {
      runnable.run();
    }
    else {
      try {
        SwingUtilities.invokeAndWait(runnable);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException {
    invokeAndWait(runnable, getDefaultModalityState());
  }

  @Override
  public @NotNull ModalityState getCurrentModalityState() {
    return getNoneModalityState();
  }

  @Override
  public @NotNull ModalityState getAnyModalityState() {
    return AnyModalityState.ANY;
  }

  @Override
  public @NotNull ModalityState getModalityStateForComponent(@NotNull Component c) {
    return getNoneModalityState();
  }

  @Override
  public @NotNull ModalityState getDefaultModalityState() {
    return getNoneModalityState();
  }

  @Override
  public void saveAll() {
  }

  @Override
  public void saveSettings() {
  }

  @Override
  public boolean holdsReadLock() {
    return false;
  }

  @Override
  public void restart(boolean exitConfirmed) {
  }

  @Override
  public void restart(boolean exitConfirmed, boolean elevate) {
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     boolean modal,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent,
                                                     @Nullable String cancelText) {
    return false;
  }

  @Override
  public void assertIsDispatchThread(final @Nullable JComponent component) {
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

  @Override
  public void dispose() {
    // A mock application may cause incorrect caching during tests. It does not fire extension point removed events.
    // Ensure that we have cached against correct application.
    MetaLanguage.clearAllMatchingMetaLanguagesCache();
    super.dispose();
  }
}
