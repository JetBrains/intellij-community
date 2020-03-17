// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class JsonSchemaInjectorBase implements MultiHostInjector {
  public static class InjectedLanguageData {
    InjectedLanguageData(@NotNull Language language, @Nullable String prefix, @Nullable String postfix) {
      this.language = language;
      this.prefix = prefix;
      this.postfix = postfix;
    }

    @NotNull public Language language;
    @Nullable public String prefix;
    @Nullable public String postfix;
  }

  protected static void injectForHost(@NotNull MultiHostRegistrar registrar, @NotNull JsonStringLiteral host, @SuppressWarnings("SameParameterValue") @NotNull Language language) {
    injectForHost(registrar, host, new InjectedLanguageData(language, null, null));
  }

  protected static void injectForHost(@NotNull MultiHostRegistrar registrar, @NotNull JsonStringLiteral host, @NotNull InjectedLanguageData language) {
    List<Pair<TextRange, String>> fragments = host.getTextFragments();
    if (fragments.isEmpty()) return;
    registrar.startInjecting(language.language);
    for (Pair<TextRange, String> fragment : fragments) {
      registrar.addPlace(language.prefix, language.postfix, (PsiLanguageInjectionHost)host, fragment.first);
    }
    registrar.doneInjecting();
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(JsonStringLiteral.class);
  }
}
