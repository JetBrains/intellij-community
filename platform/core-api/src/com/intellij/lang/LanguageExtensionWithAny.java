// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class LanguageExtensionWithAny<T> extends LanguageExtension<T> {
  public LanguageExtensionWithAny(String epName) {
    super(epName);
  }

  @Override
  protected @NotNull @Unmodifiable List<T> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    return buildExtensions(getAllBaseLanguageIdsWithAny(key));
  }

  @Override
  public void invalidateCacheForExtension(@NotNull String key) {
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

  private @NotNull @Unmodifiable Set<String> getAllBaseLanguageIdsWithAny(@NotNull Language key) {
    Set<String> allowed = new HashSet<>();
    while (key != null) {
      allowed.add(keyToString(key));
      for (MetaLanguage metaLanguage : MetaLanguage.all()) {
        if (metaLanguage.matchesLanguage(key)) {
          allowed.add(metaLanguage.getID());
        }
      }
      key = key.getBaseLanguage();
    }
    allowed.add("any");
    return allowed;
  }
}
