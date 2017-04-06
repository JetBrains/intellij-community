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
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Allows to register a language extension for a group of languages defined by a certain criterion.
 * To use this, specify the ID of a meta-language in the "language" attribute of an extension in plugin.xml.
 *
 * @author yole
 */
public abstract class MetaLanguage extends Language {
  public static final ExtensionPointName<MetaLanguage> EP_NAME = ExtensionPointName.create("com.intellij.metaLanguage");

  protected MetaLanguage(@NotNull String ID) {
    super(ID);
  }

  @NotNull
  public static MetaLanguage[] all() {
    return Extensions.getExtensions(EP_NAME);
  }

  /**
   * Checks if the given language matches the criterion of this meta-language.
   */
  public abstract boolean matchesLanguage(@NotNull Language language);

  /**
   * Returns the list of all languages matching this meta-language.
   */
  public Collection<Language> getMatchingLanguages() {
    return Language.getRegisteredLanguages()
      .stream()
      .filter(language -> matchesLanguage(language))
      .collect(Collectors.toList());
  }
}
