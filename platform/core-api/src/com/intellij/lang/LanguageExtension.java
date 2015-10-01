/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  @SuppressWarnings("ConstantConditions")
  public T forLanguage(@NotNull Language l) {
    T cached = l.getUserData(IN_LANGUAGE_CACHE);
    if (cached != null) return cached;

    List<T> extensions = forKey(l);
    T result;
    if (extensions.isEmpty()) {
      Language base = l.getBaseLanguage();
      result = base == null ? myDefaultImplementation : forLanguage(base);
    }
    else {
      result = extensions.get(0);
    }
    if (result == null) return null;
    result = l.putUserDataIfAbsent(IN_LANGUAGE_CACHE, result);
    return result;
  }

  /**
   *  @see #allForLanguageOrAny(Language)
   */
  @NotNull
  public List<T> allForLanguage(@NotNull Language l) {
    List<T> list = forKey(l);
    if (list.isEmpty()) {
      Language base = l.getBaseLanguage();
      if (base != null) {
        return allForLanguage(base);
      }
    }
    return list;
  }

  @NotNull
  public List<T> allForLanguageOrAny(@NotNull Language l) {
    List<T> providers = allForLanguage(l);
    if (l == Language.ANY) return providers;
    return ContainerUtil.concat(providers, allForLanguage(Language.ANY));
  }

  protected T getDefaultImplementation() {
    return myDefaultImplementation;
  }

  @NotNull
  protected Key<T> getLanguageCache() {
    return IN_LANGUAGE_CACHE;
  }
}
