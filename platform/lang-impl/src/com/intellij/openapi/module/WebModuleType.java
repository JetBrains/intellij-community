package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WebModuleGenerationStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
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
  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModuleBuilder moduleBuilder, ModulesProvider modulesProvider) {
    if (wizardContext.isTemplateMode()) return ModuleWizardStep.EMPTY_ARRAY;
    WebModuleGenerationStep generationStep = new WebModuleGenerationStep(
      moduleBuilder,
      wizardContext.getStepIcon(),
      "reference.dialogs.new.project.fromScratch.webModuleGeneration"
    );
    return new ModuleWizardStep[]{generationStep};
  }

  @Override
  public ModuleBuilder createModuleBuilder() {
    return new WebModuleBuilder();
  }
}
