// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ModuleProblemDescriptorImpl extends CommonProblemDescriptorImpl implements ModuleProblemDescriptor {
  private final Module myModule;

  ModuleProblemDescriptorImpl(@NotNull Module module, @NotNull @InspectionMessage String descriptionTemplate, QuickFix<?> @Nullable [] fixes) {
    super(descriptionTemplate, fixes);
    myModule = module;
  }

  @Override
  public @NotNull Module getModule() {
    return myModule;
  }
}
