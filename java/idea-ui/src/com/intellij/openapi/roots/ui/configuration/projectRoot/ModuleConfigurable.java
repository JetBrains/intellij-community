/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ModuleConfigurable extends ProjectStructureElementConfigurable<Module> implements Place.Navigator {
  private final Module myModule;
  private final ModuleGrouper myModuleGrouper;
  private final ModulesConfigurator myConfigurator;
  private String myModuleName;
  private final ModuleProjectStructureElement myProjectStructureElement;
  private final StructureConfigurableContext myContext;

  public ModuleConfigurable(ModulesConfigurator modulesConfigurator, Module module, Runnable updateTree, ModuleGrouper moduleGrouper) {
    super(true, updateTree);
    myModule = module;
    myModuleGrouper = moduleGrouper;
    myConfigurator = modulesConfigurator;
    myModuleName = ObjectUtils.notNull(myConfigurator.getModuleModel().getNewName(myModule), myModule.getName());
    myContext = ModuleStructureConfigurable.getInstance(myModule.getProject()).getContext();
    myProjectStructureElement = new ModuleProjectStructureElement(myContext, myModule);
  }

  @Override
  public void setDisplayName(String name) {
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
  protected void checkName(@NotNull String name) throws ConfigurationException {
    super.checkName(name);
    if (myModuleGrouper.getShortenedNameByFullModuleName(name).isEmpty()) {
      throw new ConfigurationException("Short name of a module cannot be empty");
    }
    List<String> list = myModuleGrouper.getGroupPathByModuleName(name);
    if (list.stream().anyMatch(s -> s.isEmpty())) {
      throw new ConfigurationException("Names of parent groups for a module cannot be empty");
    }
  }

  public ModuleGrouper getModuleGrouper() {
    return myModuleGrouper;
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

  public ModuleEditor getModuleEditor() {
    return myConfigurator.getModuleEditor(myModule);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, final boolean requestFocus) {
    ModuleEditor editor = getModuleEditor();
    return editor == null ? ActionCallback.REJECTED : editor.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    final ModuleEditor editor = getModuleEditor();
    if (editor != null) {
      editor.queryPlace(place);
    }
  }
}
