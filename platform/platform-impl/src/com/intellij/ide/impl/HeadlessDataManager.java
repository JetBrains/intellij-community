// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.awt.*;
import java.util.Map;
import java.util.function.Supplier;

public final class HeadlessDataManager extends DataManagerImpl {

  private static final class HeadlessContext extends CustomizedDataContext implements UserDataHolder {
    private final DataProvider myProvider;
    private final DataContext myParent;
    private Map<Key<?>, Object> myUserData;

    HeadlessContext(@Nullable DataProvider provider, @Nullable DataContext parent) {
      myProvider = provider;
      myParent = parent;
    }

    @Override
    public @NotNull DataContext getParent() {
      return myParent == null ? EMPTY_CONTEXT : myParent;
    }

    @Override
    public @Nullable Object getRawCustomData(@NotNull String dataId) {
      return myProvider == null ? null : myProvider.getData(dataId);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      //noinspection unchecked
      return (T)getOrCreateMap().get(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      getOrCreateMap().put(key, value);
    }

    private @NotNull Map<Key<?>, Object> getOrCreateMap() {
      Map<Key<?>, Object> userData = myUserData;
      if (userData == null) {
        myUserData = userData = ContainerUtil.createWeakValueMap();
      }
      return userData;
    }
  }

  private volatile DataProvider myTestDataProvider;
  private volatile boolean myUseProductionDataManager = false;

  @TestOnly
  public void setTestDataProvider(@Nullable DataProvider provider) {
    myTestDataProvider = provider;
  }

  @TestOnly
  public void setTestDataProvider(@Nullable DataProvider provider, @NotNull Disposable parentDisposable) {
    DataProvider previous = myTestDataProvider;
    myTestDataProvider = provider;
    Disposer.register(parentDisposable, () -> myTestDataProvider = previous);
  }

  /**
   * By default {@link HeadlessDataManager} never traverses across Swing component hierarchy.
   * This method enables usage of production {@link DataManagerImpl} in the test mode.
   *
   * @param disposable Specifies when the forwarding should be unregistered.
   * @throws IllegalStateException If already called and still not disposed.
   */
  @TestOnly
  public static void fallbackToProductionDataManager(@NotNull Disposable disposable) {
    var manager = (HeadlessDataManager)DataManager.getInstance();
    if (manager.myUseProductionDataManager) {
      throw new IllegalStateException("Already called and still not disposed.");
    }

    Disposer.register(disposable, () -> {
      manager.myUseProductionDataManager = false;
    });
    manager.myUseProductionDataManager = true;
  }

  @Override
  public @NotNull DataContext getDataContext() {
    return new HeadlessContext(myTestDataProvider, productionDataContext(super::getDataContext));
  }

  @Override
  public @NotNull Promise<DataContext> getDataContextFromFocusAsync() {
    AsyncPromise<DataContext> promise = new AsyncPromise<>();
    promise.setResult(getDataContext());
    return promise;
  }

  @Override
  public @NotNull DataContext getDataContext(Component component) {
    return new HeadlessContext(myTestDataProvider, productionDataContext(() -> super.getDataContext(component)));
  }

  @Override
  public @NotNull DataContext getDataContext(@NotNull Component component, int x, int y) {
    return new HeadlessContext(myTestDataProvider, productionDataContext(() -> super.getDataContext(component, x, y)));
  }

  private @Nullable DataContext productionDataContext(@NotNull Supplier<? extends @NotNull DataContext> dataContextSupplier) {
    return myUseProductionDataManager ? dataContextSupplier.get() : null;
  }
}