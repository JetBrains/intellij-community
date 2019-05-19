// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Allows to register a language extension for a group of languages defined by a certain criterion.
 * To use this, specify the ID of a meta-language in the "{@code language}" attribute of an extension in {@code plugin.xml}.
 *
 * @author yole
 */
public abstract class MetaLanguage extends Language {
  public static final ExtensionPointName<MetaLanguage> EP_NAME = ExtensionPointName.create("com.intellij.metaLanguage");

  protected MetaLanguage(@NotNull String ID) {
    super(ID);
  }

  @NotNull
  public static List<MetaLanguage> all() {
    return EP_NAME.getExtensionList();
  }

  @NotNull
  public static Stream<MetaLanguage> getAllMatchingMetaLanguages(@NotNull Language language) {
    if (language instanceof MetaLanguage) return Stream.empty();
    return all().stream().filter(l -> l.matchesLanguage(language));
  }

  /**
   * Checks if the given language matches the criterion of this meta-language.
   */
  public abstract boolean matchesLanguage(@NotNull Language language);

  /**
   * Returns the list of all languages matching this meta-language.
   */
  @NotNull
  public Collection<Language> getMatchingLanguages() {
    return ContainerUtil.filter(Language.getRegisteredLanguages(), language -> matchesLanguage(language));
  }
}
