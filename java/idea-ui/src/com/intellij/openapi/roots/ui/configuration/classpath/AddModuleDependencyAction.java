/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class AddModuleDependencyAction extends AddItemPopupAction<Module> {
  private final StructureConfigurableContext myContext;

  AddModuleDependencyAction(final ClasspathPanel classpathPanel,
                                   int actionIndex,
                                   StructureConfigurableContext context) {
    super(classpathPanel, actionIndex, JavaUiBundle.message("classpath.add.module.dependency.action"),
          StdModuleTypes.JAVA.getIcon());
    myContext = context;
  }

  @Override
  protected ClasspathTableItem<?> createTableItem(final Module item) {
    return ClasspathTableItem.createItem(myClasspathPanel.getRootModel().addModuleOrderEntry(item), myContext);
  }

  private List<Module> getNotAddedModules() {
    final ModifiableRootModel rootModel = myClasspathPanel.getRootModel();
    Set<Module> addedModules = ContainerUtil.set(rootModel.getModuleDependencies(true));
    addedModules.add(rootModel.getModule());

    final Module[] modules = myClasspathPanel.getModuleConfigurationState().getModulesProvider().getModules();
    final List<Module> elements = new ArrayList<>();
    for (final Module module : modules) {
      if (!addedModules.contains(module)) {
        elements.add(module);
      }
    }
    return elements;
  }

  @Override
  protected ClasspathElementChooser<Module> createChooser() {
    final List<Module> chooseItems = getNotAddedModules();
    if (chooseItems.isEmpty()) {
      Messages.showMessageDialog(myClasspathPanel.getComponent(), JavaUiBundle.message("message.no.module.dependency.candidates"), getTitle(),
                                 Messages.getInformationIcon());
      return null;
    }
    return new ModuleChooser(myClasspathPanel, chooseItems, JavaUiBundle.message("classpath.chooser.title.add.module.dependency"),
                             JavaUiBundle.message("classpath.chooser.description.add.module.dependency"));
  }

  private static class ModuleChooser implements ClasspathElementChooser<Module> {
    private final List<? extends Module> myItems;
    private final @NlsContexts.DialogTitle String myTitle;
    private final @NlsContexts.Label String myDescription;
    private final ClasspathPanel myClasspathPanel;

    ModuleChooser(final ClasspathPanel classpathPanel,
                  final List<? extends Module> items,
                  final @NlsContexts.DialogTitle String title,
                  @NlsContexts.Label String description) {
      myItems = items;
      myTitle = title;
      myDescription = description;
      myClasspathPanel = classpathPanel;
    }

    @Override
    @NotNull
    public List<Module> chooseElements() {
      ChooseModulesDialog dialog = new ChooseModulesDialog(myClasspathPanel.getComponent(), myItems, myTitle, myDescription);
      dialog.show();
      return dialog.getChosenElements();
    }
  }
}
