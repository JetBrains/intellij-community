// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public interface Named {

  @NlsSafe @NotNull String getExternalName();
  void setExternalName(@NotNull String name);

  @NotNull
  String getInternalName();
  void setInternalName(@NotNull String name);
}
