// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.TechnologyFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;
import static kotlinx.collections.immutable.ExtensionsKt.toPersistentList;

public class LanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T defaultImplementation;
  private final /* non static!!! */ Key<T> cacheKey;
  private final /* non static!!! */ Key<AllExtensions<T>> allCacheKey;
  private final /* non static!!! */ Key<PersistentList<T>> allCacheWithDefaultKey;

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
    this.defaultImplementation = defaultImplementation;
    cacheKey = Key.create("EXTENSIONS_IN_LANGUAGE_" + epName);
    allCacheKey = Key.create("ALL_EXTENSIONS_IN_LANGUAGE_" + epName);
    allCacheWithDefaultKey = Key.create("ALL_EXTENSIONS_IN_LANGUAGE_" + epName + "_WITH_DEFAULT");
  }

  @Override
  protected @NotNull String keyToString(@NotNull Language key) {
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
    language.putUserData(cacheKey, null);
    language.putUserData(allCacheKey, null);
    language.putUserData(allCacheWithDefaultKey, null);
    super.invalidateCacheForExtension(language.getID());
  }

  @Override
  public void invalidateCacheForExtension(@NotNull String key) {
    super.invalidateCacheForExtension(key);

    final Language language = Language.findLanguageByID(key);
    if (language != null) {
      clearCacheForDerivedLanguages(language);
    }
  }

  public @UnknownNullability T forLanguage(@NotNull Language l) {
    T cached = l.getUserData(cacheKey);
    if (cached != null) return cached;

    T result = findForLanguage(l);
    if (result == null) return null;
    result = l.putUserDataIfAbsent(cacheKey, result);
    return result;
  }

  protected T findForLanguage(@NotNull Language language) {
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<T> extensions = forKey(l);
      if (!extensions.isEmpty()) {
        return extensions.get(0);
      }
    }
    return defaultImplementation;
  }

  /**
   * Returns all extensions registered for the language and basic dialects.
   * Note: It does not include {@link #defaultImplementation}, if you need use {@link #allForLanguageWithDefault(Language)} instead.
   *
   *  @see #allForLanguageOrAny(Language)
   */
  public @NotNull @Unmodifiable List<T> allForLanguage(@NotNull Language language) {
    return allExtensions(language).instances;
  }

  /**
   * The extensions of {@link #allForLanguage(Language)} that apply to the project, see {@link TechnologyFilter}.
   * The module of the bean decides if an extension applies. An explicit extension applies to every project.
   * The instances are the same for every project. A project with fewer technologies does not create them again.
   */
  @ApiStatus.Experimental
  public @NotNull @Unmodifiable List<T> allForLanguage(@NotNull Language language, @NotNull Project project) {
    AllExtensions<T> all = allExtensions(language);
    TechnologyFilter filter = TechnologyFilter.getInstance();
    if (!all.hasModules || !filter.isRestricted(project)) {
      return all.instances;
    }
    return ContainerUtil.mapNotNull(all.withModules, extension -> extension.isApplicable(filter, project) ? extension.getInstance() : null);
  }

  private @NotNull AllExtensions<T> allExtensions(@NotNull Language language) {
    AllExtensions<T> cached = language.getUserData(allCacheKey);
    if (cached != null) {
      return cached;
    }
    PersistentList<ExtensionWithModule<T>> result = collectAllForLanguage(language);
    return language.putUserDataIfAbsent(allCacheKey, new AllExtensions<>(result));
  }

  private @NotNull PersistentList<ExtensionWithModule<T>> collectAllForLanguage(@NotNull Language language) {
    PersistentList<ExtensionWithModule<T>> result = persistentListOf();
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      result = result.addingAll(buildExtensionsWithModules(keyToString(l), l));
    }
    return result;
  }

  /**
   * Returns all extensions registered for the language and basic dialects, including the default implementation when it is available.
   *  @see #allForLanguage(Language)
   */
  public final @NotNull @Unmodifiable List<T> allForLanguageWithDefault(@NotNull Language language) {
    if (defaultImplementation == null) {
      return allForLanguage(language);
    }
    List<T> cached = language.getUserData(allCacheWithDefaultKey);
    if (cached != null) {
      return cached;
    }

    PersistentList<T> result = allExtensions(language).instances;
    if (!result.contains(defaultImplementation)) {
      result = result.adding(defaultImplementation);
    }
    return language.putUserDataIfAbsent(allCacheWithDefaultKey, result);
  }

  @Override
  protected @NotNull @Unmodifiable List<T> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    Set<String> allKeys = keysWithMetaLanguages(stringKey, key);
    return allKeys == null ? super.buildExtensions(stringKey, key) : buildExtensions(allKeys);
  }

  @ApiStatus.Internal
  @Override
  protected @NotNull @Unmodifiable List<ExtensionWithModule<T>> buildExtensionsWithModules(@NotNull String stringKey, @NotNull Language key) {
    Set<String> allKeys = keysWithMetaLanguages(stringKey, key);
    return allKeys == null ? super.buildExtensionsWithModules(stringKey, key) : buildExtensionsWithModules(allKeys);
  }

  /** The key with the keys of the meta-languages that match the language, or {@code null} when no meta-language does. */
  private @Nullable Set<String> keysWithMetaLanguages(@NotNull String stringKey, @NotNull Language key) {
    List<MetaLanguage> metaLanguages = LanguageUtil.matchingMetaLanguages(key);
    if (metaLanguages.isEmpty()) {
      return null;
    }

    Set<String> allKeys = new HashSet<>(metaLanguages.size() + 1);
    allKeys.add(stringKey);
    for (MetaLanguage language : metaLanguages) {
      allKeys.add(keyToString(language));
    }
    return allKeys;
  }

  /** The extensions of {@link #allForLanguageOrAny(Language)} that apply to the project, see {@link #allForLanguage(Language, Project)}. */
  @ApiStatus.Experimental
  public @Unmodifiable @NotNull List<T> allForLanguageOrAny(@NotNull Language l, @NotNull Project project) {
    List<T> forLanguage = allForLanguage(l, project);
    if (l == Language.ANY) {
      return forLanguage;
    }
    return ContainerUtil.concat(forLanguage, allForLanguage(Language.ANY, project));
  }

  public @Unmodifiable @NotNull List<T> allForLanguageOrAny(@NotNull Language l) {
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
    return defaultImplementation;
  }

  @Override
  protected void ensureValuesLoaded() {
    super.ensureValuesLoaded();
  }

  /**
   * The cached value of {@link #allForLanguage(Language)}: the extensions with their modules,
   * and the plain list of them for the callers that do not give a project.
   */
  private static final class AllExtensions<T> {
    final @NotNull PersistentList<ExtensionWithModule<T>> withModules;
    final @NotNull PersistentList<T> instances;
    /** Whether an extension has a module. When none has one, {@link #instances} is the answer for every project. */
    final boolean hasModules;

    AllExtensions(@NotNull PersistentList<ExtensionWithModule<T>> withModules) {
      this.withModules = withModules;
      List<T> instances = new ArrayList<>(withModules.size());
      boolean anyModule = false;
      for (ExtensionWithModule<T> extension : withModules) {
        instances.add(extension.getInstance());
        anyModule |= extension.getModule() != null;
      }
      this.instances = instances.isEmpty() ? persistentListOf() : toPersistentList(instances);
      this.hasModules = anyModule;
    }
  }
}
