package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;

/**
 * Provides 'import from external model' functionality.
 * 
 * @author Denis Zhdanov
 * @since 7/29/11 3:45 PM
 */
public abstract class AbstractExternalProjectImportProvider extends ProjectImportProvider {

  public AbstractExternalProjectImportProvider(ProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
<<<<<<< HEAD
    return new ModuleWizardStep[] { new SelectExternalProjectStepBase(context) };
    // TODO den uncomment
    //return new ModuleWizardStep[] { new SelectExternalProjectStepBase(context), new AdjustExternalProjectImportSettingsStep(context) };
=======
    return new ModuleWizardStep[] { new SelectExternalProjectStepBase(context), new AdjustExternalProjectImportSettingsStep(context) };
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getPath();
  }
}
