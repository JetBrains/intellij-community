// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ExternalToolsGroup extends BaseExternalToolsGroup<Tool> {

  @Override
  public @NotNull String getDelegateGroupId() {
    return ToolManager.getInstance().getRootGroupId();
  }
}
