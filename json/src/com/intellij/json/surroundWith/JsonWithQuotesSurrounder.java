// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class JsonWithQuotesSurrounder extends JsonSurrounderBase {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.quotes.desc");
  }

  @NotNull
  @Override
  protected String createReplacementText(@NotNull String firstElement) {
    return "\"" + StringUtil.escapeStringCharacters(firstElement) + "\"";
  }
}
