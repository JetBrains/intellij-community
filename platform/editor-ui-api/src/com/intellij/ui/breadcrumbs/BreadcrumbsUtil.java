// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.breadcrumbs;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class BreadcrumbsUtil {

  public static BreadcrumbsProvider getInfoProvider(@NotNull Language language) {
    BreadcrumbsProvider[] providers = BreadcrumbsProvider.EP_NAME.getExtensions();
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
