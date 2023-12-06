// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import com.intellij.formatting.IndentImpl;
import org.jetbrains.annotations.NotNull;

public final class ExpandableIndent extends IndentImpl {
  private boolean myEnforceIndent;

  public ExpandableIndent(@NotNull Type type) {
    this(type, false);
  }

  public ExpandableIndent(@NotNull Type type, boolean relativeToDirectParent) {
    super(type, false, 0, relativeToDirectParent, true);
    myEnforceIndent = false;
  }

  @Override
  public boolean isEnforceIndentToChildren() {
    return myEnforceIndent;
  }

  void enforceIndent() {
    myEnforceIndent = true;
  }

  @Override
  public String toString() {
    return "SmartIndent (" + getType() + ")";
  }
}
