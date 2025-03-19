// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionPerformer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface FallbackInjectionPerformer extends LanguageInjectionPerformer {

  void registerSupportIfNone(PsiElement context, Injection injection);

  static @Nullable FallbackInjectionPerformer getInstance() {
    return ApplicationManager.getApplication().getService(FallbackInjectionPerformer.class);
  }
}
