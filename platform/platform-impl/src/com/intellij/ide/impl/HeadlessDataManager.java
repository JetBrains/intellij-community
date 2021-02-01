// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.Disposable;
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

public class HeadlessDataManager extends DataManagerImpl {

  private static final class HeadlessContext implements DataContext, UserDataHolder {
    private final DataProvider myProvider;
    private final DataContext myParent;
    private Map<Key<?>, Object> myUserData;

    HeadlessContext(@Nullable DataProvider provider, @Nullable DataContext parent) {
      myProvider = provider;
      myParent = parent;
    }

    @Override
    @Nullable
    public Object getData(@NotNull String dataId) {
      Object result = getDataFromSelfOrParent(dataId);
      if (result == null) {
        GetDataRule rule = ((DataManagerImpl)DataManager.getInstance()).getDataRule(dataId);
        if (rule != null) {
          return rule.getData(this::getDataFromSelfOrParent);
        }
      }
      return result;
    }

    @Nullable
    private Object getDataFromSelfOrParent(@NotNull String dataId) {
      Object result = myProvider == null ? null : myProvider.getData(dataId);
      return result != null ? result :
             myParent != null ? myParent.getData(dataId) : null;
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

  @NotNull
  @Override
  public DataContext getDataContext() {
    return new HeadlessContext(myTestDataProvider, productionDataContext(super::getDataContext));
  }

  @NotNull
  @Override
  public Promise<DataContext> getDataContextFromFocusAsync() {
    AsyncPromise<DataContext> promise = new AsyncPromise<>();
    promise.setResult(getDataContext());
    return promise;
  }

  @NotNull
  @Override
  public DataContext getDataContext(Component component) {
    return new HeadlessContext(myTestDataProvider, productionDataContext(() -> super.getDataContext(component)));
  }

  @NotNull
  @Override
  public DataContext getDataContext(@NotNull Component component, int x, int y) {
    return new HeadlessContext(myTestDataProvider, productionDataContext(() -> super.getDataContext(component, x, y)));
  }

  @Nullable
  private DataContext productionDataContext(@NotNull Supplier<? extends @NotNull DataContext> dataContextSupplier) {
    return myUseProductionDataManager ? dataContextSupplier.get() : null;
  }
}