package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Provides 'import from external model' functionality.
 *
 * @author Denis Zhdanov
 * @since 7/29/11 3:45 PM
 */
public abstract class AbstractExternalProjectImportProvider extends ProjectImportProvider {
  
  @NotNull private final ProjectSystemId myExternalSystemId;
  
  public AbstractExternalProjectImportProvider(ProjectImportBuilder builder, @NotNull ProjectSystemId externalSystemId) {
    super(builder);
    myExternalSystemId = externalSystemId;
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return new ModuleWizardStep[] { new SelectExternalProjectStep(context) };
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getPath();
  }
}
