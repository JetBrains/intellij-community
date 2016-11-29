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

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;

import java.util.ArrayList;
import java.util.List;

public class DefaultModuleEditorsProvider implements ModuleConfigurationEditorProvider {
  @Override
  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    ModifiableRootModel rootModel = state.getRootModel();
    Module module = rootModel.getModule();
    if (!(ModuleType.get(module) instanceof JavaModuleType)) {
      return ModuleConfigurationEditor.EMPTY;
    }

    String moduleName = module.getName();
    List<ModuleConfigurationEditor> editors = new ArrayList<>();
    editors.add(new ContentEntriesEditor(moduleName, state));
    editors.add(new OutputEditor(state));
    editors.add(new ClasspathEditor(state));
    return editors.toArray(new ModuleConfigurationEditor[editors.size()]);
  }
}