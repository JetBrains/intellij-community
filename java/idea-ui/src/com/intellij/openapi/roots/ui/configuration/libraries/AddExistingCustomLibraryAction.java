/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;

import javax.swing.*;

/**
 * @author nik
 */
public class AddExistingCustomLibraryAction extends DumbAwareAction {
  private Library myLibrary;
  private StructureConfigurableContext myContext;
  private ModuleStructureConfigurable myModuleStructureConfigurable;
  private Module myModule;

  public AddExistingCustomLibraryAction(Library library, Icon icon, StructureConfigurableContext context,
                                        ModuleStructureConfigurable moduleStructureConfigurable, Module module) {
    super(library.getName(), null, icon);
    myLibrary = library;
    myContext = context;
    myModuleStructureConfigurable = moduleStructureConfigurable;
    myModule = module;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ModifiableRootModel rootModel = myContext.getModulesConfigurator().getOrCreateModuleEditor(myModule).getModifiableRootModelProxy();
    final LibraryOrderEntry orderEntry = rootModel.addLibraryEntry(myLibrary);
    myModuleStructureConfigurable.selectOrderEntry(myModule, orderEntry);
  }
}
