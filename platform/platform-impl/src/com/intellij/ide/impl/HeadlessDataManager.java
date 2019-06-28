// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

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

public class HeadlessDataManager extends DataManagerImpl {
  private static class HeadlessContext extends UserDataHolderBase implements DataContext {
    private final DataProvider myProvider;

    HeadlessContext(DataProvider provider) {
      myProvider = provider;
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      return myProvider != null ? myProvider.getData(dataId) : null;
    }
  }

  private volatile DataProvider myTestDataProvider;

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

  @NotNull
  @Override
  public DataContext getDataContext() {
    return new HeadlessContext(myTestDataProvider);
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
    return getDataContext();
  }

  @NotNull
  @Override
  public DataContext getDataContext(@NotNull Component component, int x, int y) {
    return getDataContext();
  }
}