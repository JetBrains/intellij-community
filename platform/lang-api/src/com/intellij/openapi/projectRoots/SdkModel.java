/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.projectRoots;

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

  /**
   * Removes a listener for receiving notifications about changes in the list.
   */
  void removeListener(@NotNull Listener listener);

  @NotNull
  Listener getMulticaster();
}
