/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.lang;

import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CompositeLanguage extends Language {
  private final List<LanguageFilter> myFilters = ContainerUtil.createLockFreeCopyOnWriteList();

  protected CompositeLanguage(final String id) {
    super(id);
  }

  protected CompositeLanguage(final String ID, final String... mimeTypes) {
    super(ID, mimeTypes);
  }

  protected CompositeLanguage(Language baseLanguage, final String ID, final String... mimeTypes) {
    super(baseLanguage, ID, mimeTypes);
  }

  public void registerLanguageExtension(LanguageFilter filter) {
    if (!myFilters.contains(filter)) myFilters.add(filter);
  }

  public boolean unregisterLanguageExtension(LanguageFilter filter) {
    return myFilters.remove(filter);
  }

  @NotNull
  public Language[] getLanguageExtensionsForFile(@NotNull PsiFile psi) {
    final List<Language> extensions = new ArrayList<>(1);
    for (LanguageFilter filter : myFilters) {
      if (filter.isRelevantForFile(psi)) extensions.add(filter.getLanguage());
    }
    return extensions.toArray(new Language[extensions.size()]);
  }

  @NotNull
  public LanguageFilter[] getLanguageExtensions() {
    return myFilters.toArray(new LanguageFilter[0]);
  }
}
