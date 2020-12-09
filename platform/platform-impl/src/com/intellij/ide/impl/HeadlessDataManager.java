// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.awt.*;
import java.util.function.Supplier;

public class HeadlessDataManager extends DataManagerImpl {
  private static final class HeadlessContext extends UserDataHolderBase implements DataContext {
    private final @Nullable DataProvider myProvider1;
    private final @Nullable DataProvider myProvider2;

    HeadlessContext(@Nullable DataProvider provider1, @Nullable DataProvider provider2) {
      myProvider1 = provider1;
      myProvider2 = provider2;
    }

    @Override
    @Nullable
    public Object getData(@NotNull String dataId) {
      if (myProvider1 != null) {
        var result = myProvider1.getData(dataId);
        if (result != null) {
          return result;
        }
      }
      if (myProvider2 != null) {
        var result = myProvider2.getData(dataId);
        if (result != null) {
          return result;
        }
      }
      return null;
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
   * By default {@link HeadlessDataManager} never traverses across Swing component hierarchy and never calls any
   * {@link com.intellij.ide.impl.dataRules.GetDataRule}. This method enables usage of production {@link DataManagerImpl} in the test mode.
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
    return new HeadlessContext(myTestDataProvider, productionDataProvider(super::getDataContext));
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
    return new HeadlessContext(myTestDataProvider, productionDataProvider(() -> super.getDataContext(component)));
  }

  @NotNull
  @Override
  public DataContext getDataContext(@NotNull Component component, int x, int y) {
    return new HeadlessContext(myTestDataProvider, productionDataProvider(() -> super.getDataContext(component, x, y)));
  }

  private DataProvider productionDataProvider(Supplier<@NotNull DataContext> dataContextSupplier) {
    return myUseProductionDataManager
           ? dataId -> dataContextSupplier.get().getData(dataId)
           : null;
  }
}