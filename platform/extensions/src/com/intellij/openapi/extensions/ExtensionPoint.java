// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter;
import com.intellij.util.containers.ContainerUtil;
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
public interface ExtensionPoint<T> {
  @NotNull
  String getName();

  /**
   * @deprecated Use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions} or {@link #registerExtension(Object, Disposable)}.
   */
  @Deprecated
  void registerExtension(@NotNull T extension);

  /**
   * @deprecated Use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions} or {@link #registerExtension(Object, LoadingOrder, Disposable)}.
   */
  @Deprecated
  void registerExtension(@NotNull T extension, @NotNull LoadingOrder order);

  @TestOnly
  void registerExtension(@NotNull T extension, @NotNull Disposable parentDisposable);

  /**
   * Use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions(ExtensionPointName, List, Disposable)}
   * to register extension as first or to completely replace existing extensions in tests.
   */
  @TestOnly
  void registerExtension(@NotNull T extension, @NotNull LoadingOrder order, @NotNull Disposable parentDisposable);

  /**
   * Prefer to use {@link #getExtensionList()}.
   */
  @NotNull
  T[] getExtensions();

  @NotNull
  List<T> getExtensionList();

  @NotNull
  Stream<T> extensions();

  boolean hasAnyExtensions();

  /**
   * @deprecated Use another solution, because this method instantiates all extensions.
   */
  @Nullable
  @Deprecated
  default T getExtension() {
    // method is deprecated and not used, ignore not efficient implementation
    return ContainerUtil.getFirstItem(getExtensionList());
  }

  /**
   * @deprecated Use another solution, because this method instantiates all extensions.
   */
  @Deprecated
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
  void unregisterExtensions(@NotNull Predicate<? super T> extension);

  /**
   * Unregisters an extension of the specified type.
   *
   * Please note that you can deregister service specifying empty implementation class.
   *
   * Consider to use {@link ExtensionNotApplicableException} instead.
   */
  @SuppressWarnings("unused")
  void unregisterExtension(@NotNull Class<? extends T> extensionClass);

  /**
   * Unregisters extensions for which the specified predicate returns false.
   *
   * Consider to use {@link ExtensionNotApplicableException} instead.
   */
  boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter, boolean stopAfterFirstMatch);

  /**
   * @deprecated use {@link #addExtensionPointListener(ExtensionPointListener, boolean, Disposable)}
   */
  @Deprecated
  void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener);

  void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener, boolean invokeForLoadedExtensions, @Nullable Disposable parentDisposable);

  void removeExtensionPointListener(@NotNull ExtensionPointListener<T> extensionPointListener);

  void reset();

  @NotNull
  String getClassName();

  /**
   * @return true if the EP allows adding/removing extensions at runtime
   */
  boolean isDynamic();

  enum Kind {INTERFACE, BEAN_CLASS}
}
