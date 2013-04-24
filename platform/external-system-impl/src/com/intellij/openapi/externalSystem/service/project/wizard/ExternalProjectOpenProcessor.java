package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 11/1/11 4:45 PM
 */
public class ExternalProjectOpenProcessor extends ProjectOpenProcessorBase<AbstractExternalProjectImportBuilder> {
  
  // TODO den implement
  public static final String[] BUILD_FILE_NAMES = { "build.gradle" };
  
  public ExternalProjectOpenProcessor(@NotNull AbstractExternalProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  public String[] getSupportedExtensions() {
    return BUILD_FILE_NAMES;
  }

  @Override
  protected boolean doQuickImport(VirtualFile file, WizardContext wizardContext) {
    return false;
    // TODO den uncomment
    //AddModuleWizard dialog = new AddModuleWizard(null, file.getPath(), new AbstractExternalProjectImportProvider(getBuilder()));
    //getBuilder().prepare(wizardContext);
    //getBuilder().setCurrentProjectPath(file.getPath());
    //dialog.getWizardContext().setProjectBuilder(getBuilder());
    //dialog.navigateToStep(new Function<Step, Boolean>() {
    //  @Override
    //  public Boolean fun(Step step) {
    //    return step instanceof SelectExternalProjectStepBase;
    //  }
    //});
    //dialog.doNextAction();
    //if (StringUtil.isEmpty(wizardContext.getProjectName())) {
    //  final String projectName = dialog.getWizardContext().getProjectName();
    //  if (!StringUtil.isEmpty(projectName)) {
    //    wizardContext.setProjectName(projectName);
    //  }
    //}
    //
    //dialog.show();
    //return dialog.isOK();
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }
}
