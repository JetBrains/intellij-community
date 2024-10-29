// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;


import com.intellij.codeInsight.hints.filtering.Matcher;
import com.intellij.codeInsight.hints.filtering.MatcherConstructor;
import com.intellij.codeInsight.hints.settings.Diff;
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings;
import com.intellij.lang.Language;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.hints.HintUtilsKt.getLanguageForSettingKey;

public final class MethodInfoExcludeListFilter implements HintInfoFilter {
  private final List<Matcher> myMatchers;

  public MethodInfoExcludeListFilter(Set<String> list) {
    myMatchers = list.stream()
      .map((item) -> MatcherConstructor.INSTANCE.createMatcher(item))
      .filter((e) -> e != null)
      .collect(Collectors.toList());
  }

  public static @NotNull MethodInfoExcludeListFilter forLanguage(@NotNull Language language) {
    Set<String> list = fullExcludelist(language);
    return new MethodInfoExcludeListFilter(list);
  }

  @Override
  public boolean showHint(@NotNull HintInfo info) {
    if (info instanceof HintInfo.MethodInfo methodInfo) {
      return !ContainerUtil.exists(myMatchers, (e) -> e.isMatching(methodInfo.getFullyQualifiedName(), methodInfo.getParamNames()));
    }
    return false;
  }

  private static @NotNull @Unmodifiable Set<String> fullExcludelist(Language language) {
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider == null) {
      return Collections.emptySet();
    }

    Set<String> excludeList = excludeList(language);
    Language dependentLanguage = provider.getBlackListDependencyLanguage();
    if (dependentLanguage != null) {
      excludeList = ContainerUtil.union(excludeList, excludeList(dependentLanguage));
    }
    return excludeList;
  }

  private static @NotNull Set<String> excludeList(@NotNull Language language) {
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider != null) {
      ParameterNameHintsSettings settings = ParameterNameHintsSettings.getInstance();
      Diff diff = settings.getExcludeListDiff(getLanguageForSettingKey(language));
      return diff.applyOn(provider.getDefaultBlackList());
    }
    return Collections.emptySet();
  }

}