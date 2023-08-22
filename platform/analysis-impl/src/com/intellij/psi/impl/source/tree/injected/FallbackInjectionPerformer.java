// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionPerformer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface FallbackInjectionPerformer extends LanguageInjectionPerformer {

  void registerSupportIfNone(PsiElement context, Injection injection);

  @Nullable
  static FallbackInjectionPerformer getInstance() {
    return ApplicationManager.getApplication().getService(FallbackInjectionPerformer.class);
  }
}
