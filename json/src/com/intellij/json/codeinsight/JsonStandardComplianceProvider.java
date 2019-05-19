// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

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

  public abstract boolean isCommentAllowed(@NotNull PsiComment comment);

  public static boolean shouldWarnAboutComment(@NotNull PsiComment comment) {
    for (JsonStandardComplianceProvider provider : EP_NAME.getIterable(null)) {
      if (provider.isCommentAllowed(comment)) {
        return false;
      }
    }
    return true;
  }
}
