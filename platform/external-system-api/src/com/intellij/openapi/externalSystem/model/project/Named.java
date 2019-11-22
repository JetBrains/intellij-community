// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public interface Named {

  /**
   * @deprecated use {@link #getExternalName()} or {@link #getInternalName()} instead
   */
  @NotNull
  @Deprecated
  String getName();

  /**
   * @deprecated use {@link #setExternalName(String)} or {@link #setInternalName(String)} instead
   */
  @Deprecated
  void setName(@NotNull String name);

  @NotNull
  String getExternalName();
  void setExternalName(@NotNull String name);

  @NotNull
  String getInternalName();
  void setInternalName(@NotNull String name);
}
