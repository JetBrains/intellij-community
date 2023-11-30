// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Collection;
import java.util.Set;

public final class UastMetaLanguage extends MetaLanguage {
  static final class Holder {
    private static volatile @Unmodifiable Set<Language> myLanguages = initLanguages();

    static {
      UastLanguagePlugin.Companion.getExtensionPointName().addChangeListener(() -> myLanguages = initLanguages(), null);
    }

    @NotNull
    private static @Unmodifiable Set<Language> initLanguages() {
      return Set.of(ContainerUtil.map2Array(UastLanguagePlugin.Companion.getInstances(), Language.EMPTY_ARRAY, plugin -> plugin.getLanguage()));
    }

    @NotNull
    static Set<Language> getLanguages() {
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
    return Holder.myLanguages;
  }

  @Override
  public @NotNull String getDisplayName() {
    return JavaAnalysisBundle.message("uast.language.display.name");
  }
}
