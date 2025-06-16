// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;


import com.intellij.codeInsight.hints.filtering.Matcher;
import com.intellij.codeInsight.hints.filtering.MatcherConstructor;
import com.intellij.codeInsight.hints.parameters.ParameterHintsExcludeListService;
import com.intellij.lang.Language;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public final class MethodInfoExcludeListFilter implements HintInfoFilter {
  private final List<Matcher> myMatchers;

  public MethodInfoExcludeListFilter(Set<String> list) {
    myMatchers = list.stream()
      .map((item) -> MatcherConstructor.INSTANCE.createMatcher(item))
      .filter((e) -> e != null)
      .collect(Collectors.toList());
  }

  public MethodInfoExcludeListFilter(@NotNull List<Matcher> matchers) {
    myMatchers = matchers;
  }

  public static @NotNull MethodInfoExcludeListFilter forLanguage(@NotNull Language language) {
    return new MethodInfoExcludeListFilter(ParameterHintsExcludeListService.getInstance().getMatchers(language));
  }

  @Override
  public boolean showHint(@NotNull HintInfo info) {
    if (info instanceof HintInfo.MethodInfo methodInfo) {
      return !ContainerUtil.exists(myMatchers, (e) -> e.isMatching(methodInfo.getFullyQualifiedName(), methodInfo.getParamNames()));
    }
    return false;
  }
}