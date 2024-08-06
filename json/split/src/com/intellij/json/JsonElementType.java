// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JsonElementType extends IElementType {
  public JsonElementType(@NotNull @NonNls String debugName) {
    super(debugName, JsonLanguage.INSTANCE);
  }
}
