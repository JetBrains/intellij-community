// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ToolLanguageUtil {
  private ToolLanguageUtil() {
  }

  public static @NotNull Set<String> getAllMatchingLanguages(@NotNull String langId, boolean applyToDialects) {
    Language language = Language.findLanguageByID(langId);
    if (language == null) {
      // unknown language in plugin.xml, ignore
      return Set.of(langId);
    }

    Set<String> result;
    if (language instanceof MetaLanguage) {
      Collection<Language> matchingLanguages = ((MetaLanguage)language).getMatchingLanguages();
      result = new HashSet<>();
      for (Language matchingLanguage : matchingLanguages) {
        result.addAll(getLanguageWithDialects(matchingLanguage, applyToDialects));
      }
    }
    else {
      result = getLanguageWithDialects(language, applyToDialects);
    }

    return Set.copyOf(result);
  }

  private static @NotNull Set<String> getLanguageWithDialects(@NotNull Language language, boolean applyToDialects) {
    List<Language> dialects = language.getDialects();
    if (!applyToDialects || dialects.isEmpty()) return Set.of(language.getID());

    Set<String> result = new HashSet<>(1 + dialects.size());
    result.add(language.getID());
    addDialects(language, result);
    return result;
  }

  private static void addDialects(@NotNull Language language, @NotNull Set<? super String> result) {
    for (Language dialect : language.getDialects()) {
      if (result.add(dialect.getID())) {
        addDialects(dialect, result);
      }
    }
  }
}
