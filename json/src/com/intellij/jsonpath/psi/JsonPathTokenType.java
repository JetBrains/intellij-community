// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.psi;

import com.intellij.jsonpath.JsonPathLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JsonPathTokenType extends IElementType {
  JsonPathTokenType(@NotNull @NonNls String debugName) {
    super(debugName, JsonPathLanguage.INSTANCE);
  }
}
