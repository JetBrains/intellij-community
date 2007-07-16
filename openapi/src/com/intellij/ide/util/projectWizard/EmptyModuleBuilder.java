/*
 * User: anna
 * Date: 13-Jul-2007
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;

public class EmptyModuleBuilder extends ModuleBuilder{
  public boolean isOpenProjectSettingsAfter() {
    return true;
  }

  public boolean canCreateModule() {
    return false;
  }

  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    //do nothing
  }

  public ModuleType getModuleType() {
    return ModuleType.EMPTY;
  }
}