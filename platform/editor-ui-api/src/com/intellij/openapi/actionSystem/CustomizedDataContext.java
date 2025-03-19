// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CustomizedDataContext implements DataContext, UserDataHolder, AnActionEvent.InjectedDataContextSupplier {

  /**
   * Tells {@link DataContext} implementations to return {@code null} and query other providers no further.
   */
  public static final Object EXPLICIT_NULL = ObjectUtils.sentinel("explicit.null");

  private final @NotNull DataContext myParent;
  private final @Nullable UserDataHolder myDataHolder;

  private final @NotNull DataContext myCustomized;

  protected CustomizedDataContext(@NotNull DataContext parent,
                                  @NotNull DataProvider provider,
                                  @Nullable UserDataHolder dataHolder) {
    myParent = parent;
    myDataHolder = dataHolder;
    myCustomized = DataManager.getInstance().customizeDataContext(myParent, provider);
  }

  protected CustomizedDataContext(@NotNull DataContext parent, @NotNull DataProvider provider, boolean delegateUserData) {
    this(parent, provider, delegateUserData && parent instanceof UserDataHolder o ? o : new UserDataHolderBase());
  }

  private CustomizedDataContext(@NotNull DataContext parent,
                                @Nullable UserDataHolder dataHolder,
                                @NotNull DataContext customized) {
    myParent = parent;
    myDataHolder = dataHolder;
    myCustomized = customized;
  }

  @Deprecated(forRemoval = true)
  protected CustomizedDataContext(boolean delegateUserData) {
    myParent = getParent();
    myDataHolder = delegateUserData && myParent instanceof UserDataHolder o ? o : new UserDataHolderBase();
    myCustomized = DataManager.getInstance().customizeDataContext(myParent, (DataProvider)this::getRawCustomData);
  }

  @Deprecated(forRemoval = true)
  protected CustomizedDataContext(@NotNull DataContext parent, boolean delegateUserData) {
    myParent = parent;
    myDataHolder = delegateUserData && parent instanceof UserDataHolder o ? o : new UserDataHolderBase();
    myCustomized = myParent;
  }

  @Deprecated(forRemoval = true)
  public CustomizedDataContext() {
    this(false);
  }

  @ApiStatus.NonExtendable
  public @NotNull DataContext getParent() {
    return myParent;
  }

  @Deprecated(forRemoval = true)
  @ApiStatus.OverrideOnly
  public @Nullable Object getRawCustomData(@NotNull String dataId) {
    return null;
  }

  @Override
  @ApiStatus.NonExtendable
  public @Nullable Object getData(@NotNull String dataId) {
    return myCustomized.getData(dataId);
  }

  @Override
  public final <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return myDataHolder == null ? null : myDataHolder.getUserData(key);
  }

  @Override
  public final <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    if (myDataHolder != null) myDataHolder.putUserData(key, value);
  }

  @ApiStatus.Internal
  public final @NotNull DataContext getCustomizedDelegate() {
    return myCustomized;
  }

  @Override
  public @NotNull DataContext getInjectedDataContext() {
    return new CustomizedDataContext(myParent, myDataHolder, AnActionEvent.getInjectedDataContext(myCustomized));
  }

  /** @deprecated Use {@link #withSnapshot} */
  @Deprecated(forRemoval = true)
  public static @NotNull CustomizedDataContext create(@NotNull DataContext parent, @NotNull DataProvider provider) {
    return new CustomizedDataContext(parent, provider, true);
  }

  /** @deprecated Use {@link #withSnapshot} */
  @Deprecated(forRemoval = true)
  public static @NotNull DataContext withProvider(@NotNull DataContext parent, @NotNull DataProvider provider) {
    DataContext customized = DataManager.getInstance().customizeDataContext(parent, provider);
    UserDataHolder holder = parent instanceof UserDataHolder o ? o :
                            customized instanceof UserDataHolder o ? o : new UserDataHolderBase();
    return new CustomizedDataContext(parent, holder, customized);
  }

  public static @NotNull DataContext withSnapshot(@NotNull DataContext parent, @NotNull DataSnapshotProvider provider) {
    DataContext customized = DataManager.getInstance().customizeDataContext(parent, provider);
    UserDataHolder holder = parent instanceof UserDataHolder o ? o :
                            customized instanceof UserDataHolder o ? o : new UserDataHolderBase();
    return new CustomizedDataContext(parent, holder, customized);
  }
}
