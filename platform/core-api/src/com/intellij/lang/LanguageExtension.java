// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class LanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T myDefaultImplementation;
  private final /* non static!!! */ Key<T> myCacheKey;
  private final /* non static!!! */ Key<List<T>> myAllCacheKey;

  public LanguageExtension(@NotNull final ExtensionPointName<? extends KeyedLazyInstance<T>> epName) {
    this(epName.getName(), null);
  }

  public LanguageExtension(@NotNull @NonNls final String epName) {
    this(epName, null);
  }

  public LanguageExtension(@NotNull final ExtensionPointName<? extends KeyedLazyInstance<T>> epName, @Nullable T defaultImplementation) {
    this(epName.getName(), defaultImplementation);
  }

  public LanguageExtension(@NonNls String epName, @Nullable T defaultImplementation) {
    super(epName);
    myDefaultImplementation = defaultImplementation;
    myCacheKey = Key.create("EXTENSIONS_IN_LANGUAGE_" + epName);
    myAllCacheKey = Key.create("ALL_EXTENSIONS_IN_LANGUAGE_" + epName);
  }

  @NotNull
  @Override
  protected String keyToString(@NotNull final Language key) {
    return key.getID();
  }

  @TestOnly
  public void clearCache(@NotNull Language language) {
    clearCacheForDerivedLanguages(language);
    clearCache();
  }

  private void clearCacheForDerivedLanguages(@NotNull Language language) {
    Set<Language> languages = LanguageUtil.getAllDerivedLanguages(language);  // includes language itself
    for (Language derivedLanguage : languages) {
      clearCacheForLanguage(derivedLanguage);

      Collection<MetaLanguage> metaLanguages = LanguageUtil.matchingMetaLanguages(derivedLanguage);
      for (MetaLanguage metaLanguage : metaLanguages) {
        clearCacheForLanguage(metaLanguage);
      }
    }
    if (language instanceof MetaLanguage) {
      Collection<Language> matchingLanguages = ((MetaLanguage)language).getMatchingLanguages();
      for (Language matchingLanguage : matchingLanguages) {
        Set<Language> matchingDerivedLanguages = LanguageUtil.getAllDerivedLanguages(matchingLanguage);  // includes language itself
        for (Language derivedLanguage : matchingDerivedLanguages) {
          clearCacheForLanguage(derivedLanguage);
        }
      }
    }
  }

  private void clearCacheForLanguage(Language language) {
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
  @NotNull
  public List<T> allForLanguage(@NotNull Language language) {
    List<T> cached = language.getUserData(myAllCacheKey);
    if (cached != null) return cached;
    List<T> result = collectAllForLanguage(language);
    return language.putUserDataIfAbsent(myAllCacheKey, result);
  }

  @NotNull
  private List<T> collectAllForLanguage(@NotNull Language language) {
    boolean copyList = true;
    List<T> result = null;
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<T> list = forKey(l);
      if (result == null) {
        result = list;
      }
      else if (!list.isEmpty()) {
        if (copyList) {
          result = new ArrayList<>(ContainerUtil.concat(result, list));
          copyList = false;
        }
        else {
          result.addAll(list);
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  protected List<T> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    Collection<MetaLanguage> metaLanguages = LanguageUtil.matchingMetaLanguages(key);
    if (metaLanguages.isEmpty()) {
      return super.buildExtensions(stringKey, key);
    }

    Set<String> allKeys = new HashSet<>();
    allKeys.add(stringKey);
    for (MetaLanguage language : metaLanguages) {
      allKeys.add(keyToString(language));
    }
    return buildExtensions(allKeys);
  }

  @NotNull
  public List<T> allForLanguageOrAny(@NotNull Language l) {
    List<T> forLanguage = allForLanguage(l);
    if (l == Language.ANY) return forLanguage;
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
