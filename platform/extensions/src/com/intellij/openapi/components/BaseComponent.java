// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * The base interface class for all components.
 */
public interface BaseComponent extends NamedComponent {
  /**
   * @deprecated Use {@link com.intellij.openapi.components.PersistentStateComponent#initializeComponent()} or perform initialization in constructor for non-persistence component.
   */
  @Deprecated
  default void initComponent() {
  }

  /**
   * @deprecated Use {@link com.intellij.openapi.Disposable}
   */
  @Deprecated
  default void disposeComponent() {
  }
}
