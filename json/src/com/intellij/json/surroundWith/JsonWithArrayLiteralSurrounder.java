// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class JsonWithArrayLiteralSurrounder extends JsonSingleValueSurrounderBase {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.array.literal.desc");
  }

  @NotNull
  @Override
  protected String createReplacementText(@NotNull PsiElement firstElement) {
    return "[" + firstElement.getText() + "]";
  }
}
