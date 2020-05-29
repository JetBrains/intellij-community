// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class UastMetaLanguage extends MetaLanguage {
  private final Set<Language> myLanguages;

  private UastMetaLanguage() {
    super("UAST");

    Collection<UastLanguagePlugin> languagePlugins = UastLanguagePlugin.Companion.getInstances();
    myLanguages = new HashSet<>(languagePlugins.size());
    initLanguages(languagePlugins);

    UastLanguagePlugin.Companion.getExtensionPointName().addChangeListener(() -> {
      myLanguages.clear();
      initLanguages(UastLanguagePlugin.Companion.getInstances());
    }, null);
  }

  private void initLanguages(Collection<UastLanguagePlugin> languagePlugins) {
    for (UastLanguagePlugin plugin : languagePlugins) {
      myLanguages.add(plugin.getLanguage());
    }
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    return myLanguages.contains(language);
  }

  @NotNull
  @Override
  public Collection<Language> getMatchingLanguages() {
    return Collections.unmodifiableSet(myLanguages);
  }
}
