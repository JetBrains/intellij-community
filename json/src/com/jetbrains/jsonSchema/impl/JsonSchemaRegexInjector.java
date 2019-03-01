// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonSchemaRegexInjector implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!JsonSchemaService.isSchemaFile(context.getContainingFile())) return;
    JsonOriginalPsiWalker walker = JsonOriginalPsiWalker.INSTANCE;
    if (!(context instanceof JsonStringLiteral)) return;
    ThreeState isName = walker.isName(context);
    JsonPointerPosition position = walker.findPosition(context, isName == ThreeState.NO);
    if (position == null || position.isEmpty()) return;
    if (isName == ThreeState.YES) {
      if ("patternProperties".equals(position.getLastName())) {
        if (isNestedInPropertiesList(position)) return;
        injectForHost(registrar, (JsonStringLiteral)context);
      }
    }
    else if (isName == ThreeState.NO) {
      if ("pattern".equals(position.getLastName())) {
        if (isNestedInPropertiesList(position)) return;
        injectForHost(registrar, (JsonStringLiteral)context);
      }
    }
  }

  private static boolean isNestedInPropertiesList(JsonPointerPosition position) {
    final JsonPointerPosition skipped = position.trimTail(1);
    return skipped != null && "properties".equals(skipped.getLastName());
  }

  private static void injectForHost(@NotNull MultiHostRegistrar registrar, @NotNull JsonStringLiteral host) {
    List<Pair<TextRange, String>> fragments = host.getTextFragments();
    if (fragments.isEmpty()) return;
    registrar.startInjecting(EcmaScriptRegexpLanguage.INSTANCE);
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
