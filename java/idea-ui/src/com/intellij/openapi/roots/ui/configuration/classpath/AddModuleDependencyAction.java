// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
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
          JavaModuleType.getModuleType().getIcon());
    myContext = context;
  }

  @Override
  protected ClasspathTableItem<?> createTableItem(final Module item) {
    return ClasspathTableItem.createItem(myClasspathPanel.getRootModel().addModuleOrderEntry(item), myContext);
  }

  private List<Module> getNotAddedModules() {
    final ModifiableRootModel rootModel = myClasspathPanel.getRootModel();
    Set<Module> addedModules = ContainerUtil.newHashSet(rootModel.getModuleDependencies(true));
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
    public @NotNull List<Module> chooseElements() {
      ChooseModulesDialog dialog = new ChooseModulesDialog(myClasspathPanel.getComponent(), myItems, myTitle, myDescription);
      dialog.show();
      return dialog.getChosenElements();
    }
  }
}
