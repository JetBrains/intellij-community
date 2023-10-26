// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.breadcrumbs;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BreadcrumbsUtil {
  public static BreadcrumbsProvider getInfoProvider(@NotNull Language language) {
    List<BreadcrumbsProvider> providers = BreadcrumbsProvider.EP_NAME.getExtensionList();
    while (language != null) {
      for (BreadcrumbsProvider provider : providers) {
        for (Language supported : provider.getLanguages()) {
          if (language.is(supported)) {
            return provider;
          }
        }
      }
      language = language.getBaseLanguage();
    }
    return null;
  }
}
