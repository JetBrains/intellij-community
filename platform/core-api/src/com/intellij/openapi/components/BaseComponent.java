// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * The base interface class for all components.
 *
 * @see ProjectComponent
 */
public interface BaseComponent extends NamedComponent {
  /**
   * Component should perform initialization and communication with other components in this method.
   * This is called after {@link com.intellij.openapi.components.PersistentStateComponent#loadState(Object)}.
   */
  default void initComponent() {
  }

  /**
   * Prefer to use {@link com.intellij.openapi.Disposable}
   */
  default void disposeComponent() {
  }
}
