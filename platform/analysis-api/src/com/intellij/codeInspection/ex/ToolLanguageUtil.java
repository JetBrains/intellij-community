// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ToolLanguageUtil {
  private ToolLanguageUtil() {
  }

  public static @NotNull @Unmodifiable Set<String> getAllMatchingLanguages(@NotNull String languageId, boolean applyToDialects) {
    Language language = Language.findLanguageByID(languageId);
    if (language == null) {
      // unknown language in plugin.xml, ignore
      return Set.of(languageId);
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
  private static boolean existsDialectOneOf(@NotNull Set<String> elementDialectIds, @NotNull Language language, boolean applyToDialects) {
    if (elementDialectIds.contains(language.getID())) {
      return true;
    }
    if (applyToDialects) {
      for (Language dialect : language.getDialects()) {
        if (existsDialectOneOf(elementDialectIds, dialect, applyToDialects)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isToolLanguageOneOf(@NotNull Set<String> elementDialectIds, @NotNull String languageId, boolean applyToDialects) {
    if (elementDialectIds.contains(languageId)) {
      return true;
    }
    Language language = Language.findLanguageByID(languageId);
    if (language == null) {
      // unknown language in plugin.xml, ignore
      return false;
    }

    if (language instanceof MetaLanguage) {
      return !ContainerUtil.process(Language.getRegisteredLanguages(), registeredLanguage ->
        !((MetaLanguage)language).matchesLanguage(registeredLanguage)
        || !existsDialectOneOf(elementDialectIds, registeredLanguage, applyToDialects));
    }
    return existsDialectOneOf(elementDialectIds, language, applyToDialects);
  }
}
