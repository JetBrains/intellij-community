// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class UastMetaLanguage extends MetaLanguage {
  static final class Holder {
    private static final Set<Language> myLanguages;

    static {
      Collection<UastLanguagePlugin> languagePlugins = UastLanguagePlugin.Companion.getInstances();
      myLanguages = new HashSet<>(languagePlugins.size());
      initLanguages(languagePlugins);

      UastLanguagePlugin.Companion.getExtensionPointName().addChangeListener(() -> {
        myLanguages.clear();
        initLanguages(UastLanguagePlugin.Companion.getInstances());
      }, null);
    }

    private static void initLanguages(Collection<UastLanguagePlugin> languagePlugins) {
      for (UastLanguagePlugin plugin : languagePlugins) {
        myLanguages.add(plugin.getLanguage());
      }
    }

    public static Set<Language> getLanguages() {
      return myLanguages;
    }
  }

  private UastMetaLanguage() {
    super("UAST");
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    return Holder.myLanguages.contains(language);
  }

  @NotNull
  @Override
  public Collection<Language> getMatchingLanguages() {
    return Collections.unmodifiableSet(Holder.myLanguages);
  }

  @Override
  public @NotNull String getDisplayName() {
    return JavaAnalysisBundle.message("uast.language.display.name");
  }
}
