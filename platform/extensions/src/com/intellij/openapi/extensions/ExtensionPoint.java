// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter;
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
@SuppressWarnings("JavadocReference")
public interface ExtensionPoint<T> {
  @NotNull
  String getName();

  AreaInstance getArea();

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

  @Nullable
  T getExtension();

  /**
   * @deprecated Use another solution, because this method instantiate all extensions.
   */
  @Deprecated
  boolean hasExtension(@NotNull T extension);

  /**
   * @deprecated Use another solutions to unregister not applicable extension, because this method instantiate all extensions.
   */
  @Deprecated
  void unregisterExtension(@NotNull T extension);

  /**
   * @deprecated Use another solutions to unregister not applicable extension, because this method instantiate all extensions.
   */
  @Deprecated
  void unregisterExtensions(@NotNull Predicate<T> extension);

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
   * Unregisters an extension of the specified type.
   *
   * Consider to use {@link ExtensionNotApplicableException} instead.
   */
  boolean unregisterExtensions(@NotNull BiPredicate<String, ExtensionComponentAdapter> extensionClassFilter, boolean stopAfterFirstMatch);

  @Deprecated
  void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener);

  void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener, boolean invokeForLoadedExtensions, @Nullable Disposable parentDisposable);

  void removeExtensionPointListener(@NotNull ExtensionPointListener<T> extensionPointListener);

  void reset();

  @NotNull
  Class<T> getExtensionClass();

  @NotNull
  String getClassName();

  enum Kind {INTERFACE, BEAN_CLASS}
}
