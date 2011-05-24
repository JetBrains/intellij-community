package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class WebModuleType extends WebModuleTypeBase<ModuleBuilder> {
  @NotNull
  public static WebModuleType getInstance() {
    return (WebModuleType)ModuleTypeManager.getInstance().findByID(WEB_MODULE);
  }

  public ModuleBuilder createModuleBuilder() {
    return new ModuleBuilder() {
      @Override
      public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
        doAddContentEntry(modifiableRootModel);
      }

      @Override
      public ModuleType getModuleType() {
        return getInstance();
      }
    };
  }
}
