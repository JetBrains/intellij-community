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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    for (MetaLanguage metaLanguage: MetaLanguage.all()) {
      if (metaLanguage.matchesLanguage(l)) {
        T result = forLanguage(metaLanguage);
        if (result != null) break;
      }
    }

    return myDefaultImplementation;
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

    if (!(l instanceof MetaLanguage)) {
      for (MetaLanguage metaLanguage : MetaLanguage.all()) {
        providers.addAll(allForLanguage(metaLanguage));
      }
    }
    return providers;
  }

  protected T getDefaultImplementation() {
    return myDefaultImplementation;
  }

  @NotNull
  protected Key<T> getLanguageCache() {
    return IN_LANGUAGE_CACHE;
  }

  @NotNull
  protected Set<String> getAllBaseLanguageIdsWithAny(@NotNull Language key) {
    Set<String> allowed = new THashSet<>();
    while (key != null) {
      allowed.add(keyToString(key));
      key = key.getBaseLanguage();
    }
    allowed.add("any");
    for (MetaLanguage metaLanguage : MetaLanguage.all()) {
      allowed.add(metaLanguage.getID());
    }
    return allowed;
  }

}
