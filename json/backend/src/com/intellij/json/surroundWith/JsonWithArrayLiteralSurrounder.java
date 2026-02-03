// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import org.jetbrains.annotations.NotNull;

public final class JsonWithArrayLiteralSurrounder extends JsonSurrounderBase {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.array.literal.desc");
  }

  @Override
  protected @NotNull String createReplacementText(@NotNull String firstElement) {
    return "[" + firstElement + "]";
  }
}
