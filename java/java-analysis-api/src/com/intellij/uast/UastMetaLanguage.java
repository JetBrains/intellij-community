// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class UastMetaLanguage extends MetaLanguage {
  private final Set<Language> myLanguages = new HashSet<>();

  protected UastMetaLanguage() {
    super("UAST");
    for (UastLanguagePlugin plugin: UastLanguagePlugin.Companion.getInstances()) {
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
    return myLanguages;
  }
}
