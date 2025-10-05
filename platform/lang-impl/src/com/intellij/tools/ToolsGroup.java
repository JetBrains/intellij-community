// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.openapi.options.CompoundScheme;
import org.jetbrains.annotations.NotNull;

public final class ToolsGroup<T extends Tool> extends CompoundScheme<T> {
  public ToolsGroup(@NotNull String name) {
    super(name);
  }

  void moveElementUp(T tool) {
    int index = myElements.indexOf(tool);
    myElements.remove(index);
    myElements.add(index - 1, tool);
  }

  void moveElementDown(T tool) {
    int index = myElements.indexOf(tool);
    myElements.remove(index);
    myElements.add(index + 1, tool);
  }
}
