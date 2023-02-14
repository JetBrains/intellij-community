// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.MetaLanguage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class CompletionExtension<T> extends LanguageExtension<T> {
  public CompletionExtension(String epName) {
    super(epName);
  }

  @NotNull
  @Override
  protected List<T> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    return buildExtensions(getAllBaseLanguageIdsWithAny(key));
  }

  @Override
  public void invalidateCacheForExtension(String key) {
    super.invalidateCacheForExtension(key);
    // clear the entire cache because, if languages are unloaded, we won't be able to find cache keys for unloaded dialects of
    // a given language
    clearCache();

    if ("any".equals(key)) {
      for (Language language : Language.getRegisteredLanguages()) {
        super.invalidateCacheForExtension(keyToString(language));
      }
    }
  }

  @NotNull
  private Set<String> getAllBaseLanguageIdsWithAny(@NotNull Language key) {
    Set<String> allowed = new HashSet<>();
    while (key != null) {
      allowed.add(keyToString(key));
      for (MetaLanguage metaLanguage : MetaLanguage.all()) {
        if (metaLanguage.matchesLanguage(key))
          allowed.add(metaLanguage.getID());
      }
      key = key.getBaseLanguage();
    }
    allowed.add("any");
    return allowed;
  }
}
