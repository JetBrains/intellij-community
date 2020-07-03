// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

/**
 * Represents the current state of the SDK list in the <em>SDKs</em>
 * configuration dialog.
 */
public interface SdkModel {

  /**
   * Returns the list of SDKs in the table.
   */
  Sdk @NotNull [] getSdks();

  /**
   * Returns the SDK with the specified name, or {@code null} if none found.
   */
  @Nullable
  Sdk findSdk(String sdkName);

  /**
   * Adds the specified SDK (already created and initialized) to the model.
   */
  void addSdk(@NotNull Sdk sdk);

  /**
   * Allows receiving notifications when the SDK list has been modified.
   */
  interface Listener extends EventListener {

    /**
     * Called when a SDK has been added.
     */
    default void sdkAdded(@NotNull Sdk sdk) {}

    /**
     * Called before a SDK is removed.
     *
     * @param sdk the removed JDK.
     */
    default void beforeSdkRemove(@NotNull Sdk sdk) {}

    /**
     * Called when a SDK has been changed or renamed.
     */
    default void sdkChanged(@NotNull Sdk sdk, String previousName) {}

    /**
     * Called when the home directory of a SDK has been changed.
     */
    default void sdkHomeSelected(@NotNull Sdk sdk, @NotNull String newSdkHome) {}
  }

  /**
   * Adds a listener for receiving notifications about changes in the list.
   */
  void addListener(@NotNull Listener listener);

  default void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable) {
    addListener(listener);
    Disposer.register(parentDisposable, () -> removeListener(listener));
  }

  /**
   * Removes a listener for receiving notifications about changes in the list.
   */
  void removeListener(@NotNull Listener listener);

  @NotNull
  Listener getMulticaster();
}
