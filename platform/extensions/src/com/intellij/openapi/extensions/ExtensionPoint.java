// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @see com.intellij.testFramework.PlatformTestUtil#maskExtensions
 */
public interface ExtensionPoint<@NotNull T> {
  /**
   * @deprecated Use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions} or {@link #registerExtension(Object, Disposable)}.
   */
  @Deprecated
  default void registerExtension(@NotNull T extension) {
    registerExtension(extension, LoadingOrder.ANY);
  }

  /**
   * @deprecated Use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions} or {@link #registerExtension(Object, LoadingOrder, Disposable)}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void registerExtension(@NotNull T extension, @NotNull LoadingOrder order);

  @TestOnly
  void registerExtension(@NotNull T extension, @NotNull Disposable parentDisposable);

  @TestOnly
  void registerExtension(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor, @NotNull Disposable parentDisposable);

  /**
   * Use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions}
   * to register extension as first or to completely replace existing extensions in tests.
   */
  @TestOnly
  void registerExtension(@NotNull T extension, @NotNull LoadingOrder order, @NotNull Disposable parentDisposable);

  /**
   * Prefer to use {@link #getExtensionList()}.
   */
  T @NotNull [] getExtensions();

  @NotNull
  List<T> getExtensionList();

  @NotNull
  Stream<T> extensions();

  int size();

  /**
   * @deprecated Use another solution, because this method instantiates all extensions.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default @Nullable T getExtension() {
    // method is deprecated and not used, ignore not efficient implementation
    return ContainerUtil.getFirstItem(getExtensionList());
  }

  /**
   * @deprecated Use another solution, because this method instantiates all extensions.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default boolean hasExtension(@NotNull T extension) {
    // method is deprecated and used only by one external plugin, ignore not efficient implementation
    return ContainerUtil.containsIdentity(getExtensionList(), extension);
  }

  /**
   * @deprecated Use another solution to unregister not applicable extension, because this method instantiates all extensions.
   */
  @Deprecated
  void unregisterExtension(@NotNull T extension);

  /**
   * @deprecated Use another solution to unregister not applicable extension, because this method instantiates all extensions.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void unregisterExtensions(@NotNull Predicate<? super T> extension);

  /**
   * Unregisters an extension of the specified type.
   *
   * Please note that you can deregister service specifying empty implementation class.
   *
   * Consider to use {@link ExtensionNotApplicableException} instead.
   */
  void unregisterExtension(@NotNull Class<? extends T> extensionClass);

  /**
   * Unregisters extensions for which the specified predicate returns false.
   *
   * Consider to use {@link ExtensionNotApplicableException} instead.
   */
  boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter, boolean stopAfterFirstMatch);

  void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener, boolean invokeForLoadedExtensions, @Nullable Disposable parentDisposable);

  /**
   * @deprecated Use {@link ExtensionPointName#addChangeListener(Runnable, Disposable)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void addExtensionPointListener(@NotNull ExtensionPointChangeListener listener, boolean invokeForLoadedExtensions, @Nullable Disposable parentDisposable);

  /**
   * Consider using {@link ExtensionPointName#addChangeListener}
   */
  void addChangeListener(@NotNull Runnable listener, @Nullable Disposable parentDisposable);

  @ApiStatus.Internal
  void removeExtensionPointListener(@NotNull ExtensionPointListener<T> extensionPointListener);

  @NotNull
  String getClassName();

  /**
   * @return true if the EP allows adding/removing extensions at runtime
   */
  boolean isDynamic();

  @NotNull
  PluginDescriptor getPluginDescriptor();

  enum Kind {INTERFACE, BEAN_CLASS}
}
