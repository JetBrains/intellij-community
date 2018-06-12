/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

/**
 * Represents the current state of the JDK list in the JDK and Global Libraries
 * configuration dialog.
 */
public interface SdkModel {

  /**
   * Returns the list of SDKs in the table.
   * @return the SDK list.
   */
  Sdk[] getSdks();

  /**
   * Returns the SDK with the specified name, or null if one is not found.
   *
   * @param sdkName the name of the SDK to find.
   * @return the SDK instance or null.
   */
  @Nullable
  Sdk findSdk(String sdkName);

  /**
   * Adds the specified SDK (already created and initialized) to the model.
   *
   * @param sdk the SDK to add
   */
  void addSdk(Sdk sdk);

  /**
   * Allows to receive notifications when the JDK list has been changed by the
   * user configuring the JDKs.
   */

  interface Listener extends EventListener {
    /**
     * Called when a JDK has been added.
     *
     * @param sdk the added JDK.
     */
    default void sdkAdded(Sdk sdk) {}

    /**
     * Called before a JDK is removed.
     *
     * @param sdk the removed JDK.
     */
    default void beforeSdkRemove(Sdk sdk) {}

    /**
     * Called when a JDK has been changed or renamed.
     *
     * @param sdk          the changed or renamed JDK.
     * @param previousName the old name of the changed or renamed JDK.
     * @since 5.0.1
     */
    default void sdkChanged(Sdk sdk, String previousName) {}

    /**
     * Called when the home directory of a JDK has been changed.
     * @param sdk        the changed JDK.
     * @param newSdkHome the new home directory.
     */
    default void sdkHomeSelected(Sdk sdk, String newSdkHome) {}

  }

  /**
   * Adds a listener for receiving notifications about changes in the list.
   *
   * @param listener the listener instance.
   */
  void addListener(Listener listener);

  /**
   * Removes a listener for receiving notifications about changes in the list.
   *
   * @param listener the listener instance.
   */
  void removeListener(Listener listener);

  Listener getMulticaster();
}
