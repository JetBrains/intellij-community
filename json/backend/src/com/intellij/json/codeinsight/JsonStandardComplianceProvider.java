// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

import com.intellij.json.psi.JsonElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiComment;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to configure a compliance level for JSON.
 * For example, some tools ignore comments in JSON silently when parsing, so there is no need to warn users about it.
 */
public abstract class JsonStandardComplianceProvider {
  public static final ExtensionPointName<JsonStandardComplianceProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.json.jsonStandardComplianceProvider");

  public static boolean shouldWarnAboutComment(@NotNull PsiComment comment) {
    return EP_NAME.findFirstSafe(provider -> provider.isCommentAllowed(comment)) == null;
  }

  public static boolean shouldWarnAboutTrailingComma(@NotNull JsonElement el) {
    return EP_NAME.findFirstSafe(provider -> provider.isTrailingCommaAllowed(el)) == null;
  }
  
  public boolean isCommentAllowed(@NotNull PsiComment comment) {
    return false;
  }
  
  public boolean isTrailingCommaAllowed(@NotNull JsonElement el) {
    return false;
  }
}