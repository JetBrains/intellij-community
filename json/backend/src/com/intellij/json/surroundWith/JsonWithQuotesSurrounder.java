// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class JsonWithQuotesSurrounder extends JsonSurrounderBase {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.quotes.desc");
  }

  @Override
  protected @NotNull String createReplacementText(@NotNull String firstElement) {
    return "\"" + StringUtil.escapeStringCharacters(firstElement) + "\"";
  }
}
