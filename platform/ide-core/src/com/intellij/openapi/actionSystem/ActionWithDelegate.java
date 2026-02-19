// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ Platform uses logging and statistics for actions. Please use this interface for actions with the same action class
 * but different business logic passed in constructor.
 *
 * @author Konstantin Bulenkov
 */
public interface ActionWithDelegate<T> {
  @NotNull
  T getDelegate();

  /**
   *
   * @deprecated not used, for FUS use ActionIdProvider#getId
   */
  @Deprecated
  default String getPresentableName() {
    return getDelegate().getClass().getName();
  }
}
