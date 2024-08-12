// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import org.jetbrains.annotations.NotNull;


public class WebModuleType extends WebModuleTypeBase<ModuleBuilder> {

  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return new WebModuleBuilder();
  }

  public @NotNull <T> ModuleBuilder createModuleBuilder(@NotNull WebProjectTemplate<T> webProjectTemplate) {
    return new WebModuleBuilder<>(webProjectTemplate);
  }
}
