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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class JsonSchemaInjectorBase implements MultiHostInjector {
  protected static void injectForHost(@NotNull MultiHostRegistrar registrar, @NotNull JsonStringLiteral host, @NotNull Language language) {
    List<Pair<TextRange, String>> fragments = host.getTextFragments();
    if (fragments.isEmpty()) return;
    registrar.startInjecting(language);
    for (Pair<TextRange, String> fragment : fragments) {
      registrar.addPlace(null, null, (PsiLanguageInjectionHost)host, fragment.first);
    }
    registrar.doneInjecting();
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return ContainerUtil.list(JsonStringLiteral.class);
  }
}
