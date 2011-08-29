package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.NullableComputable;

import java.util.Collection;
import java.util.Collections;

public abstract class ModuleStructureExtension {

  public static final ExtensionPointName<ModuleStructureExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.configuration.ModuleStructureExtension");

  public void reset() {
  }

  public boolean addModuleNodeChildren(Module module,
                                       MasterDetailsComponent.MyNode moduleNode,
                                       ModifiableRootModel modifiableRootModel,
                                       Runnable treeNodeNameUpdater) {
    return false;
  }

  //public void moduleAdded(final Module module, final Runnable treeNodeNameUpdater) {
  //}

  public void moduleRemoved(final Module module) {
  }

  /**
   * TODO remove this
   * @Deprecated
   */
  @Deprecated
  public boolean isModulesConfiguratorModified() {
    return false;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void disposeUIResources() {
  }

  public boolean canBeRemoved(final Object editableObject) {
    return false;
  }

  public boolean removeObject(final Object editableObject) {
    return false;
  }

  public Collection<AnAction> createAddActions(final NullableComputable<MasterDetailsComponent.MyNode> selectedNodeRetriever,
                                               final Runnable treeNodeNameUpdater,
                                               final ModulesConfigurator modulesConfigurator) {
    return Collections.emptyList();
  }

  public boolean canBeCopied(final NamedConfigurable confugurable) {
    return false;
  }

  public void copy(final NamedConfigurable confugurable, final Runnable treeNodeNameUpdater) {
  }
}
