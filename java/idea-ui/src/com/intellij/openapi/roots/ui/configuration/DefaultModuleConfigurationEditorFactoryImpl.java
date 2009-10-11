/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 28, 2004
 */
public class DefaultModuleConfigurationEditorFactoryImpl extends DefaultModuleConfigurationEditorFactory {
  public ModuleConfigurationEditor createModuleContentRootsEditor(ModuleConfigurationState state) {
    final ModifiableRootModel rootModel = state.getRootModel();
    final Module module = rootModel.getModule();
    final String moduleName = module.getName();
    return new ContentEntriesEditor(state.getProject(), moduleName, rootModel, state.getModulesProvider());
  }

  public ModuleConfigurationEditor createClasspathEditor(ModuleConfigurationState state) {
    return new ClasspathEditor(state.getProject(), state.getRootModel(), state.getModulesProvider());
  }

  public ModuleConfigurationEditor createJavadocEditor(ModuleConfigurationState state) {
    return new JavadocEditor(state.getProject(), state.getRootModel());
  }

  public ModuleConfigurationEditor createOutputEditor(ModuleConfigurationState state) {
    return new OutputEditor(state.getProject(), state.getRootModel());
  }

  @Deprecated
  public ModuleConfigurationEditor createCompilerOutputEditor(ModuleConfigurationState state) {
    return new BuildElementsEditor(state.getProject(), state.getRootModel());
  }
}
