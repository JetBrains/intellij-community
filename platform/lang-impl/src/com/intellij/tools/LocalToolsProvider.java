// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public final class LocalToolsProvider extends ToolsProvider {
  @Override
  public List<Tool> getTools() {
    return ToolManager.getInstance().getTools();
  }
}
