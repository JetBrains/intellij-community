package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.ModuleConfigurationEditor;

public interface ModuleConfigurationEditorProvider {
  ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state);
}