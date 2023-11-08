// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows an action to retrieve information about the context in which it was invoked.
 * <p/>
 * <b>NOTES:</b>
 * <ul>
 * <li>Avoid overriding or implementing this interface.
 * Things have got more complex since the introduction of asynchronous action update.
 * If you need to alter the provided data context or create one from a set of data
 * use {@link CustomizedDataContext} or {@link SimpleDataContext} instead, even in tests.
 * These classes are async-ready, optionally support {@link com.intellij.openapi.util.UserDataHolder}, and run {@link GetDataRule} rules.</li>
 * <li>Do not to confuse {@link DataProvider} with {@link DataContext}.
 * A {@link DataContext} is usually provided by the platform with {@link DataProvider}s as its building blocks.
 * For example, a node in a tree view could be a {@link DataProvider} but not a {@link DataContext}.</li>
 * </ul>
 *
 * @see AnActionEvent#getDataContext()
 * @see com.intellij.openapi.actionSystem.CommonDataKeys
 * @see com.intellij.openapi.actionSystem.LangDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformCoreDataKeys
 * @see DataKey
 * @see com.intellij.ide.DataManager
 * @see DataProvider
 */
@FunctionalInterface
public interface DataContext {
  /**
   * Returns the object corresponding to the specified data identifier. Some of the supported
   * data identifiers are defined in the {@link com.intellij.openapi.actionSystem.PlatformDataKeys} class.
   * <br>
   * <b>NOTE:</b> For implementation only, prefer {@link DataContext#getData(DataKey)} in client code.
   *
   * @param dataId the data identifier for which the value is requested.
   * @return the value, or null if no value is available in the current context for this identifier.
   */
  @Nullable Object getData(@NotNull String dataId);

  DataContext EMPTY_CONTEXT = dataId -> null;

  /**
   * Returns the value corresponding to the specified data key. Some of the supported
   * data identifiers are defined in the {@link com.intellij.openapi.actionSystem.PlatformDataKeys} class.
   *
   * @param key the data key for which the value is requested.
   * @return the value, or null if no value is available in the current context for this identifier.
   */
  @ApiStatus.NonExtendable
  default @Nullable <T> T getData(@NotNull DataKey<T> key) {
    //noinspection unchecked
    return (T)getData(key.getName());
  }
}
