// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class DefaultModuleEditorsProvider implements ModuleConfigurationEditorProvider {
  @Override
  public @NotNull ModuleConfigurationEditor @NotNull [] createEditors(@NotNull ModuleConfigurationState state) {
    Module module = state.getCurrentRootModel().getModule();
    ModuleType<?> moduleType = ModuleType.get(module);
    if (!(moduleType instanceof JavaModuleType) && !GeneralModuleType.INSTANCE.equals(moduleType)) {
      return ModuleConfigurationEditor.EMPTY;
    }

    String moduleName = module.getName();
    List<ModuleConfigurationEditor> editors = new ArrayList<>();
    editors.add(new ContentEntriesEditor(moduleName, state));
    editors.add(new OutputEditor(state));
    editors.add(new ClasspathEditor(state));
    return editors.toArray(ModuleConfigurationEditor.EMPTY);
  }
}