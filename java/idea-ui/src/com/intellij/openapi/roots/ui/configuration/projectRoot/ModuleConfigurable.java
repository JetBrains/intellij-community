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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */
public class ModuleConfigurable extends ProjectStructureElementConfigurable<Module> implements Place.Navigator {
  private final Module myModule;
  private final ModulesConfigurator myConfigurator;
  private String myModuleName;
  private final ModuleProjectStructureElement myProjectStructureElement;
  private final StructureConfigurableContext myContext;

  public ModuleConfigurable(ModulesConfigurator modulesConfigurator,
                            Module module,
                            final Runnable updateTree) {
    super(true, updateTree);
    myModule = module;
    myModuleName = myModule.getName();
    myConfigurator = modulesConfigurator;
    myContext = ModuleStructureConfigurable.getInstance(myModule.getProject()).getContext();
    myProjectStructureElement = new ModuleProjectStructureElement(myContext, myModule);
  }

  @Override
  public void setDisplayName(String name) {
    name = name.trim();
    final ModifiableModuleModel modifiableModuleModel = myConfigurator.getModuleModel();
    if (StringUtil.isEmpty(name)) return; //empty string comes on double click on module node
    if (Comparing.strEqual(name, myModuleName)) return; //nothing changed
    try {
      modifiableModuleModel.renameModule(myModule, name);
    }
    catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
      //do nothing
    }
    myConfigurator.moduleRenamed(myModule, myModuleName, name);
    myModuleName = name;
    myConfigurator.setModified(!Comparing.strEqual(myModuleName, myModule.getName()));
    myContext.getDaemonAnalyzer().queueUpdateForAllElementsWithErrors();
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  @Override
  public Module getEditableObject() {
    return myModule;
  }

  @Override
  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.module.banner.text", myModuleName);
  }

  @Override
  public String getDisplayName() {
    return myModuleName;
  }

  @Override
  public Icon getIcon(final boolean open) {
    return myModule.isDisposed() ? null : ModuleType.get(myModule).getIcon();
  }

  public Module getModule() {
    return myModule;
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    ModuleEditor editor = getModuleEditor();
    return editor == null ? null : editor.getHelpTopic();
  }


  @Override
  public JComponent createOptionsPanel() {
    ModuleEditor editor = getModuleEditor();
    return editor == null ? null : editor.getPanel();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    //do nothing
  }

  @Override
  public void reset() {
    //do nothing
  }

  @Override
  public void disposeUIResources() {
    //do nothing
  }

  public ModuleEditor getModuleEditor() {
    return myConfigurator.getModuleEditor(myModule);
  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    ModuleEditor editor = getModuleEditor();
    return editor == null ? ActionCallback.REJECTED : editor.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull final Place place) {
    final ModuleEditor editor = getModuleEditor();
    if (editor != null) {
      editor.queryPlace(place);
    }
  }

  @Override
  public void setHistory(final History history) {
  }
}
