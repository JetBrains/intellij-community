// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class JsonWithQuotesSurrounder extends JsonSingleValueSurrounderBase {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.quotes.desc");
  }

  @NotNull
  @Override
  protected String createReplacementText(@NotNull PsiElement firstElement) {
    return "\"" + StringUtil.escapeStringCharacters(firstElement.getText()) + "\"";
  }
}
