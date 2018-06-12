// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.MetaLanguage;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

class CompletionExtension<T> extends LanguageExtension<T> {

  CompletionExtension(String epName) {
    super(epName);
  }

  @NotNull
  @Override
  protected List<T> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    return buildExtensions(getAllBaseLanguageIdsWithAny(key));
  }

  @NotNull
  private Set<String> getAllBaseLanguageIdsWithAny(@NotNull Language key) {
    Set<String> allowed = new THashSet<>();
    while (key != null) {
      allowed.add(keyToString(key));
      key = key.getBaseLanguage();
    }
    allowed.add("any");
    for (MetaLanguage metaLanguage : MetaLanguage.all()) {
      allowed.add(metaLanguage.getID());
    }
    return allowed;
  }
}
