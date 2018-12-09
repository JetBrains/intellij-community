// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class CachedAnnotators {
  private final CachedValue<ThreadLocalAnnotatorMap<String, Annotator>> myCache = new CachedValueImpl<>(() -> {
    ThreadLocalAnnotatorMap<String, Annotator> map = new ThreadLocalAnnotatorMap<String, Annotator>() {
      @NotNull
      @Override
      public Collection<Annotator> initialValue(@NotNull String languageId) {
        Language language = Language.findLanguageByID(languageId);
        return language == null ? ContainerUtil.emptyList() : LanguageAnnotators.INSTANCE.allForLanguageOrAny(language);
      }
    };
    return CachedValueProvider.Result.create(map, LanguageAnnotators.INSTANCE);
  });

  @NotNull
  List<Annotator> get(@NotNull String languageId) {
    return myCache.getValue().get(languageId);
  }
}
