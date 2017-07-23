/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.breadcrumbs;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class BreadcrumbsUtil {

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
