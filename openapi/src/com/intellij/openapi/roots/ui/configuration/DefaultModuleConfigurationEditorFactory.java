/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.module.ModuleConfigurationEditor;

public abstract class DefaultModuleConfigurationEditorFactory implements ApplicationComponent {

  public abstract ModuleConfigurationEditor createModuleContentRootsEditor(ModuleConfigurationState state);

  public abstract ModuleConfigurationEditor createLibrariesEditor(ModuleConfigurationState state);

  public abstract ModuleConfigurationEditor createDependenciesEditor(ModuleConfigurationState state);

  public abstract ModuleConfigurationEditor createOrderEntriesEditor(ModuleConfigurationState state);

  public abstract ModuleConfigurationEditor createJavadocEditor(ModuleConfigurationState state);

  public static DefaultModuleConfigurationEditorFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(DefaultModuleConfigurationEditorFactory.class);
  }

}
