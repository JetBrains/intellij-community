// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author yole
 */
public class UastMetaLanguage extends MetaLanguage {
  private final Set<Language> myLanguages;

  protected UastMetaLanguage() {
    super("UAST");

    Collection<UastLanguagePlugin> languagePlugins = UastLanguagePlugin.Companion.getInstances();
    myLanguages = new THashSet<>(languagePlugins.size());
    for (UastLanguagePlugin plugin: languagePlugins) {
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
