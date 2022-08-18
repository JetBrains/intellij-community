// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

/**
 * @deprecated Components are deprecated, please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html">SDK Docs</a> for guidelines on migrating to other APIs.
 */
@Deprecated
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
