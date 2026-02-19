/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.lang;

import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CompositeLanguage extends Language {
  private final ConcurrentList<LanguageFilter> myFilters = ContainerUtil.createConcurrentList();

  protected CompositeLanguage(@NotNull String id) {
    super(id);
  }

  protected CompositeLanguage(@NotNull String ID, @NotNull String @NotNull ... mimeTypes) {
    super(ID, mimeTypes);
  }

  protected CompositeLanguage(@NotNull Language baseLanguage, @NotNull String ID, @NotNull String @NotNull ... mimeTypes) {
    super(baseLanguage, ID, mimeTypes);
  }

  public void registerLanguageExtension(@NotNull LanguageFilter filter) {
    myFilters.addIfAbsent(filter);
  }

  @ApiStatus.Internal
  public boolean unregisterLanguageExtension(@NotNull LanguageFilter filter) {
    return myFilters.remove(filter);
  }

  public Language @NotNull [] getLanguageExtensionsForFile(@NotNull PsiFile psi) {
    final List<Language> extensions = new ArrayList<>(1);
    for (LanguageFilter filter : myFilters) {
      if (filter.isRelevantForFile(psi)) extensions.add(filter.getLanguage());
    }
    return extensions.toArray(Language.EMPTY_ARRAY);
  }

  public LanguageFilter @NotNull [] getLanguageExtensions() {
    return myFilters.toArray(new LanguageFilter[0]);
  }
}
