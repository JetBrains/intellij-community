// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LangDataValidators extends DataValidators {
  @Override
  protected void collectValidators(@NotNull ValidatorRegistry registry) {
    Validator<PsiElement> psiValidator = (data, dataId, source) -> data.isValid();
    registry.register(CommonDataKeys.PSI_FILE, psiValidator);
    registry.register(CommonDataKeys.PSI_ELEMENT, psiValidator);
    registry.register(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, arrayValidator(psiValidator));

    Validator<Module> moduleValidator = (data, dataId, source) -> !data.isDisposed();
    registry.register(PlatformCoreDataKeys.MODULE, moduleValidator);
    registry.register(LangDataKeys.MODULE_CONTEXT, moduleValidator);
    registry.register(LangDataKeys.MODULE_CONTEXT_ARRAY, arrayValidator(moduleValidator));
  }
}