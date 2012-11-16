package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class WebModuleType extends WebModuleTypeBase<ModuleBuilder> {
  @NotNull
  public static WebModuleType getInstance() {
    return (WebModuleType)ModuleTypeManager.getInstance().findByID(WEB_MODULE);
  }

  @Override
  public ModuleBuilder createModuleBuilder() {
    return new WebModuleBuilder();
  }
}
