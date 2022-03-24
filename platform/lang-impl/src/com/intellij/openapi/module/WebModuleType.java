// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import org.jetbrains.annotations.NotNull;


public class WebModuleType extends WebModuleTypeBase<ModuleBuilder> {

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return new WebModuleBuilder();
  }

  @NotNull
  public <T> ModuleBuilder createModuleBuilder(@NotNull WebProjectTemplate<T> webProjectTemplate) {
    return new WebModuleBuilder<>(webProjectTemplate);
  }
}
