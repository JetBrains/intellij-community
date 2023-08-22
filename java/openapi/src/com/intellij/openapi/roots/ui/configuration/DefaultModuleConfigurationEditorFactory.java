// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleConfigurationEditor;

public abstract class DefaultModuleConfigurationEditorFactory {

  public abstract ModuleConfigurationEditor createModuleContentRootsEditor(ModuleConfigurationState state);

  public abstract ModuleConfigurationEditor createClasspathEditor(ModuleConfigurationState state);

  public abstract ModuleConfigurationEditor createOutputEditor(ModuleConfigurationState state);

  public abstract String getOutputEditorDisplayName();

  public static DefaultModuleConfigurationEditorFactory getInstance() {
    return ApplicationManager.getApplication().getService(DefaultModuleConfigurationEditorFactory.class);
  }

}
