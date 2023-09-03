// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.*;

public class LanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T myDefaultImplementation;
  private final /* non static!!! */ Key<T> myCacheKey;
  private final /* non static!!! */ Key<List<T>> myAllCacheKey;

  public LanguageExtension(final @NotNull ExtensionPointName<? extends KeyedLazyInstance<T>> epName) {
    this(epName.getName(), null);
  }

  public LanguageExtension(final @NotNull @NonNls String epName) {
    this(epName, null);
  }

  public LanguageExtension(final @NotNull ExtensionPointName<? extends KeyedLazyInstance<T>> epName, @Nullable T defaultImplementation) {
    this(epName.getName(), defaultImplementation);
  }

  public LanguageExtension(@NonNls String epName, @Nullable T defaultImplementation) {
    super(epName);
    myDefaultImplementation = defaultImplementation;
    myCacheKey = Key.create("EXTENSIONS_IN_LANGUAGE_" + epName);
    myAllCacheKey = Key.create("ALL_EXTENSIONS_IN_LANGUAGE_" + epName);
  }

  @Override
  protected @NotNull String keyToString(final @NotNull Language key) {
    return key.getID();
  }

  @TestOnly
  public void clearCache(@NotNull Language language) {
    clearCacheForDerivedLanguages(language);
    clearCache();
  }

  private void clearCacheForDerivedLanguages(@NotNull Language language) {
    for (Language dialect : ContainerUtil.concat(language.getTransitiveDialects(), Collections.singletonList(language))) {
      clearCacheForLanguage(dialect);
      for (MetaLanguage metaLanguage : LanguageUtil.matchingMetaLanguages(dialect)) {
        clearCacheForLanguage(metaLanguage);
      }
    }
    if (language instanceof MetaLanguage) {
      Collection<Language> matchingLanguages = ((MetaLanguage)language).getMatchingLanguages();
      for (Language matchingLanguage : matchingLanguages) {
        Collection<Language> dialects = matchingLanguage.getTransitiveDialects();
        clearCacheForLanguage(matchingLanguage);
        for (Language dialect : dialects) {
          clearCacheForLanguage(dialect);
        }
      }
    }
  }

  private void clearCacheForLanguage(@NotNull Language language) {
    language.putUserData(myCacheKey, null);
    language.putUserData(myAllCacheKey, null);
    super.invalidateCacheForExtension(language.getID());
  }

  @Override
  public void invalidateCacheForExtension(String key) {
    super.invalidateCacheForExtension(key);

    final Language language = Language.findLanguageByID(key);
    if (language != null) {
      clearCacheForDerivedLanguages(language);
    }
  }

  public T forLanguage(@NotNull Language l) {
    T cached = l.getUserData(myCacheKey);
    if (cached != null) return cached;

    T result = findForLanguage(l);
    if (result == null) return null;
    result = l.putUserDataIfAbsent(myCacheKey, result);
    return result;
  }

  protected T findForLanguage(@NotNull Language language) {
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<T> extensions = forKey(l);
      if (!extensions.isEmpty()) {
        return extensions.get(0);
      }
    }
    return myDefaultImplementation;
  }

  /**
   *  @see #allForLanguageOrAny(Language)
   */
  public @NotNull List<T> allForLanguage(@NotNull Language language) {
    List<T> cached = language.getUserData(myAllCacheKey);
    if (cached != null) return cached;
    List<T> result = collectAllForLanguage(language);
    return language.putUserDataIfAbsent(myAllCacheKey, result);
  }

  private @NotNull List<T> collectAllForLanguage(@NotNull Language language) {
    boolean copyList = true;
    List<T> result = null;
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<T> list = forKey(l);
      if (result == null) {
        result = list;
      }
      else if (!list.isEmpty()) {
        if (copyList) {
          List<T> newResult = new ArrayList<>(result.size() + list.size());
          newResult.addAll(result);
          newResult.addAll(list);
          result = newResult;
          copyList = false;
        }
        else {
          result.addAll(list);
        }
      }
    }
    return result;
  }

  @Override
  protected @NotNull List<T> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    Collection<MetaLanguage> metaLanguages = LanguageUtil.matchingMetaLanguages(key);
    if (metaLanguages.isEmpty()) {
      return super.buildExtensions(stringKey, key);
    }

    Set<String> allKeys = new HashSet<>(metaLanguages.size()+1);
    allKeys.add(stringKey);
    for (MetaLanguage language : metaLanguages) {
      allKeys.add(keyToString(language));
    }
    return buildExtensions(allKeys);
  }

  public @NotNull List<T> allForLanguageOrAny(@NotNull Language l) {
    List<T> forLanguage = allForLanguage(l);
    if (l == Language.ANY) {
      return forLanguage;
    }
    return ContainerUtil.concat(forLanguage, allForLanguage(Language.ANY));
  }

  @Override
  public void addExplicitExtension(@NotNull Language key, @NotNull T t) {
    clearCacheForLanguage(key);
    super.addExplicitExtension(key, t);
  }

  @Override
  public void removeExplicitExtension(@NotNull Language key, @NotNull T t) {
    clearCacheForLanguage(key);
    super.removeExplicitExtension(key, t);
  }

  protected T getDefaultImplementation() {
    return myDefaultImplementation;
  }

  @Override
  protected void ensureValuesLoaded() {
    super.ensureValuesLoaded();
  }
}
