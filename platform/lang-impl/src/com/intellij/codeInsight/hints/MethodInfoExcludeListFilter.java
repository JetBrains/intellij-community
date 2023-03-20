// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class MethodInfoExcludeListFilter implements HintInfoFilter {
  private final List<Matcher> myMatchers;

  public MethodInfoExcludeListFilter(Set<String> list) {
    myMatchers = list.stream()
      .map((item) -> MatcherConstructor.INSTANCE.createMatcher(item))
      .filter((e) -> e != null)
      .collect(Collectors.toList());
  }

  @NotNull
  public static MethodInfoExcludeListFilter forLanguage(@NotNull Language language) {
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

  @NotNull
  @Unmodifiable
  private static Set<String> fullExcludelist(Language language) {
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

  @NotNull
  private static Set<String> excludeList(@NotNull Language language) {
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider != null) {
      ParameterNameHintsSettings settings = ParameterNameHintsSettings.getInstance();
      Diff diff = settings.getExcludeListDiff(getLanguageForSettingKey(language));
      return diff.applyOn(provider.getDefaultBlackList());
    }
    return Collections.emptySet();
  }

}