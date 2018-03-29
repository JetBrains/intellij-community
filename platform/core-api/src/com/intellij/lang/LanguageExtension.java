// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T myDefaultImplementation;
  private final /* non static!!! */ Key<T> IN_LANGUAGE_CACHE;

  public LanguageExtension(@NonNls final String epName) {
    this(epName, null);
  }

  public LanguageExtension(@NonNls final String epName, @Nullable final T defaultImplementation) {
    super(epName);
    myDefaultImplementation = defaultImplementation;
    IN_LANGUAGE_CACHE = Key.create("EXTENSIONS_IN_LANGUAGE_"+epName);
  }

  @NotNull
  @Override
  protected String keyToString(@NotNull final Language key) {
    return key.getID();
  }

  public T forLanguage(@NotNull Language l) {
    T cached = l.getUserData(IN_LANGUAGE_CACHE);
    if (cached != null) return cached;

    T result = findForLanguage(l);
    if (result == null) return null;
    result = l.putUserDataIfAbsent(IN_LANGUAGE_CACHE, result);
    return result;
  }

  protected T findForLanguage(@NotNull Language l) {
    List<T> extensions = forKey(l);
    if (!extensions.isEmpty()) {
      return extensions.get(0);
    }

    Language base = l.getBaseLanguage();
    if (base != null) {
      return forLanguage(base);
    }

    Optional<T> forAnyMetaLanguage = MetaLanguage.getAllMatchingMetaLanguages(l)
      .map(metaLanguage -> forLanguage(metaLanguage)).filter(Objects::nonNull).findAny();

    return forAnyMetaLanguage.orElse(myDefaultImplementation);
  }

  /**
   *  @see #allForLanguageOrAny(Language)
   */
  @NotNull
  public List<T> allForLanguage(@NotNull Language language) {
    boolean copyList = true;
    List<T> result = null;
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<T> list = forKey(l);
      if (result == null) {
        result = list;
      }
      else if (!list.isEmpty()) {
        if (copyList) {
          result = ContainerUtil.newArrayList(ContainerUtil.concat(result, list));
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
  public List<T> allForLanguageOrAny(@NotNull Language l) {
    List<T> providers = new ArrayList<>(allForLanguage(l));
    if (l != Language.ANY) {
      providers.addAll(allForLanguage(Language.ANY));
    }

    MetaLanguage.getAllMatchingMetaLanguages(l).forEach(metaLanguage -> {
      providers.addAll(allForLanguage(metaLanguage));
    });

    return providers;
  }

  @Override
  public void addExplicitExtension(@NotNull Language key, @NotNull T t) {
    key.putUserData(IN_LANGUAGE_CACHE, null);
    super.addExplicitExtension(key, t);
  }

  @Override
  public void removeExplicitExtension(@NotNull Language key, @NotNull T t) {
    key.putUserData(IN_LANGUAGE_CACHE, null);
    super.removeExplicitExtension(key, t);
  }

  protected T getDefaultImplementation() {
    return myDefaultImplementation;
  }
}
