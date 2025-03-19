// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;

/**
 * Provides access to {@link DataContext}.
 * <p/>
 * Use {@link AnActionEvent#getData(DataKey)} in {@link com.intellij.openapi.actionSystem.AnAction AnAction}.
 */
public abstract class DataManager {
  private static final Logger LOG = Logger.getInstance(DataManager.class);

  public static DataManager getInstance() {
    return ApplicationManager.getApplication().getService(DataManager.class);
  }

  public static DataManager getInstanceIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(DataManager.class);
  }

  private static final String CLIENT_PROPERTY_DATA_PROVIDER = "DataProvider";

  @ApiStatus.Internal
  protected DataManager() {
  }

  /**
   * @return {@link DataContext} constructed by the currently focused component
   * @deprecated use either {@link #getDataContext(Component)} or {@link #getDataContextFromFocus()}
   */
  @Deprecated
  public abstract @NotNull DataContext getDataContext();

  /**
   * @deprecated Use {@link #getDataContextFromFocusAsync()}
   */
  @Deprecated
  public final @NotNull AsyncResult<DataContext> getDataContextFromFocus() {
    AsyncResult<DataContext> result = new AsyncResult<>();
    getDataContextFromFocusAsync()
      .onSuccess(context -> result.setDone(context))
      .onError(it -> result.reject(it.getMessage()));
    return result;
  }

  /**
   * @return {@link DataContext} constructed by the currently focused component.
   */
  public abstract @NotNull Promise<DataContext> getDataContextFromFocusAsync();

  /**
   * @return {@link DataContext} constructed by the specified {@code component}
   */
  public abstract @NotNull DataContext getDataContext(Component component);

  /**
   * @return {@link DataContext} constructed be the specified {@code component}
   * and the point specified by {@code x} and {@code y} coordinate inside the
   * component.
   * @throws IllegalArgumentException if point {@code (x, y)} is not inside component's bounds
   */
  public abstract @NotNull DataContext getDataContext(@NotNull Component component, int x, int y);

  /**
   * Returns data from the provided data context customized with the provided data provider.
   */
  @ApiStatus.Internal
  public abstract @Nullable Object getCustomizedData(@NotNull String dataId, @NotNull DataContext dataContext, @NotNull DataProvider provider);

  /**
   * Use {@link com.intellij.openapi.actionSystem.CustomizedDataContext#customize} instead
   */
  @ApiStatus.Internal
  public abstract @NotNull DataContext customizeDataContext(@NotNull DataContext dataContext, @NotNull Object provider);

  /**
   * Save doesn't work during fast action update to prevent caching of yet invalid data
   *
   * @param dataContext should be instance of {@link com.intellij.openapi.util.UserDataHolder}
   * @param dataKey     key to store value
   * @param data        value to store
   */
  public abstract <T> void saveInDataContext(@Nullable DataContext dataContext, @NotNull Key<T> dataKey, @Nullable T data);

  /**
   * @param dataContext find by key if instance of {@link com.intellij.openapi.util.UserDataHolder}
   * @param dataKey     key to find value by
   * @return value stored by {@link #saveInDataContext(DataContext, Key, Object)}
   */
  public abstract @Nullable <T> T loadFromDataContext(@NotNull DataContext dataContext, @NotNull Key<T> dataKey);

  /**
   * Implement {@link com.intellij.openapi.actionSystem.UiDataProvider} on a component directly
   * and use separate {@link com.intellij.openapi.actionSystem.UiDataRule} to add specific UI data.
   * <p>
   * Use {@link UiDataProvider#wrapComponent(JComponent, UiDataProvider)} for simple cases.
   * Use {@link EdtNoGetDataProvider} as a temporary type-safe and performant solution.
   */
  @ApiStatus.Obsolete
  public static void registerDataProvider(@NotNull JComponent component, @NotNull DataProvider provider) {
    if (component instanceof UiDataProvider) {
      LOG.warn(String.format("Registering CLIENT_PROPERTY_DATA_PROVIDER on component implementing UiDataProvider. " +
                             "The key will be ignored. Component: %s", component), new Throwable());
    }
    else if (component instanceof DataProvider) {
      LOG.warn(String.format("Registering CLIENT_PROPERTY_DATA_PROVIDER on component implementing DataProvider. " +
                             "The key will be ignored. Component: %s", component), new Throwable());
    }
    Object oldProvider = component.getClientProperty(CLIENT_PROPERTY_DATA_PROVIDER);
    if (oldProvider != null) {
      LOG.warn(String.format("Overwriting an existing CLIENT_PROPERTY_DATA_PROVIDER. " +
                             "Component: %s, old provider: %s, new provider: %s", component, oldProvider, provider), new Throwable());
    }
    component.putClientProperty(CLIENT_PROPERTY_DATA_PROVIDER, provider);
  }

  /** Most components now implement {@link UiDataProvider} */
  @ApiStatus.Obsolete
  public static @Nullable DataProvider getDataProvider(@NotNull JComponent component) {
    return (DataProvider)component.getClientProperty(CLIENT_PROPERTY_DATA_PROVIDER);
  }

  /** Most components now implement {@link UiDataProvider} */
  @ApiStatus.Obsolete
  public static void removeDataProvider(@NotNull JComponent component) {
    component.putClientProperty(CLIENT_PROPERTY_DATA_PROVIDER, null);
  }
}
