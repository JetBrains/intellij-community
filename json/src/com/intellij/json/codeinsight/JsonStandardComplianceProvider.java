/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    JsonStandardComplianceProvider[] providers = EP_NAME.getExtensions();
    if (providers.length == 0) {
      return true;
    }
    for (JsonStandardComplianceProvider provider : providers) {
      if (provider.isCommentAllowed(comment)) {
        return false;
      }
    }
    return true;
  }
}
