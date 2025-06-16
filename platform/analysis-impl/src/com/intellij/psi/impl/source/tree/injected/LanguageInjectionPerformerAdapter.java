// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.lang.injection.general.LanguageInjectionPerformer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class LanguageInjectionPerformerAdapter implements MultiHostInjector, DumbAware {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    var isDumb = DumbService.isDumb(context.getProject());

    Language language = context.getLanguage();
    Injection injection = null;
    for (LanguageInjectionContributor contributor : LanguageInjectionContributor.INJECTOR_EXTENSION.allForLanguageOrAny(language)) {
      if (isDumb && !DumbService.isDumbAware(contributor)) continue;

      injection = contributor.getInjection(context);
      if (injection != null) break;
    }

    if (injection == null) return;

    boolean primaryPerformerWasCalled = false;
    boolean injectionIsHandled = false;

    for (LanguageInjectionPerformer performer : LanguageInjectionPerformer.INJECTOR_EXTENSION.allForLanguageOrAny(language)) {
      primaryPerformerWasCalled = primaryPerformerWasCalled || performer.isPrimary();
      if (performer.performInjection(registrar, injection, context)) {
        injectionIsHandled = true;
        break;
      }
    }

    FallbackInjectionPerformer fallbackInjectionPerformer = FallbackInjectionPerformer.getInstance();
    if (fallbackInjectionPerformer != null) {
      if (!primaryPerformerWasCalled && !injectionIsHandled) {
        fallbackInjectionPerformer.performInjection(registrar, injection, context);
      }
      fallbackInjectionPerformer.registerSupportIfNone(context, injection);
    }
  }


  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return List.of(PsiElement.class);
  }
}
