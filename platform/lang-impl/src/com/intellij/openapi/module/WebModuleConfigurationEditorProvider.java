// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import org.jetbrains.annotations.NotNull;

final class WebModuleConfigurationEditorProvider implements ModuleConfigurationEditorProvider {
  @Override
  public @NotNull ModuleConfigurationEditor @NotNull [] createEditors(@NotNull ModuleConfigurationState state) {
    Module module = state.getCurrentRootModel().getModule();
    if (!ModuleTypeWithWebFeatures.isAvailable(module)) {
      return ModuleConfigurationEditor.EMPTY;
    }
    return new ModuleConfigurationEditor[]{new CommonContentEntriesEditor(module.getName(), state)};
  }
}
