// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonSchemaRegexInjector implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!JsonSchemaService.isSchemaFile(context.getContainingFile())) return;
    JsonOriginalPsiWalker walker = JsonLikePsiWalker.JSON_ORIGINAL_PSI_WALKER;
    if (!(context instanceof JsonStringLiteral)) return;
    ThreeState isName = walker.isName(context);
    List<JsonSchemaVariantsTreeBuilder.Step> position = walker.findPosition(context, isName == ThreeState.NO);
    if (position == null || position.isEmpty()) return;
    if (isName == ThreeState.YES) {
      JsonSchemaVariantsTreeBuilder.Step lastStep = ContainerUtil.getLastItem(position);
      if (lastStep != null && "patternProperties".equals(lastStep.getName())) {
        if (isNestedInPropertiesList(position)) return;
        injectForHost(registrar, (JsonStringLiteral)context);
      }
    }
    else if (isName == ThreeState.NO) {
      JsonSchemaVariantsTreeBuilder.Step lastStep = ContainerUtil.getLastItem(position);
      if (lastStep != null && "pattern".equals(lastStep.getName())) {
        if (isNestedInPropertiesList(position)) return;
        injectForHost(registrar, (JsonStringLiteral)context);
      }
    }
  }

  private static boolean isNestedInPropertiesList(List<JsonSchemaVariantsTreeBuilder.Step> position) {
    if (position.size() >= 2) {
      JsonSchemaVariantsTreeBuilder.Step prev = position.get(position.size() - 2);
      if ("properties".equals(prev.getName())) return true;
    }
    return false;
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
