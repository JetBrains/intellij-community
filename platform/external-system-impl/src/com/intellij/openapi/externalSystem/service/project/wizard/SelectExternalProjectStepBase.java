package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Handles the following responsibilities:
 * <pre>
 * <ul>
 *   <li>allows end user to define external system config file to import from;</li>
 *   <li>processes the input and reacts accordingly - shows error message if the project is invalid or proceeds to the next screen;</li>
 * </ul>
 * </pre>
 *
 * @author Denis Zhdanov
 * @since 8/1/11 4:15 PM
 */
public class SelectExternalProjectStepBase extends AbstractImportFromExternalSystemWizardStep {

  private final JPanel myComponent = new JPanel(new BorderLayout());

  @NotNull private AbstractImportFromExternalSystemControl myControl;

  private boolean myGradleSettingsInitialised;

  public SelectExternalProjectStepBase(@NotNull WizardContext context) {
    super(context);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void updateStep() {
    if (!myGradleSettingsInitialised) {
      initGradleSettingsControl();
    }
  }

  @Override
  public void updateDataModel() {
  }

  // TODO den uncomment
  //@Override
  //public String getHelpId() {
  //  return GradleConstants.HELP_TOPIC_IMPORT_SELECT_PROJECT_STEP;
  //}

  @Override
  public boolean validate() throws ConfigurationException {
    myControl.apply();
    storeCurrentSettings();
    AbstractExternalProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return false;
    }
    builder.ensureProjectIsDefined(getWizardContext());
    return true;
  }

  @Override
  public void onStepLeaving() {
    storeCurrentSettings();
  }

  private void storeCurrentSettings() {
    final String projectPath = myControl.getProjectSettings().getExternalProjectPath();
    if (projectPath != null) {
      final File parent = new File(projectPath).getParentFile();
      if (parent != null) {
        getWizardContext().setProjectName(parent.getName());
      }
    }
  }

  private void initGradleSettingsControl() {
    AbstractExternalProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return;
    }
    builder.prepare(getWizardContext());
    myControl = builder.getControl();
    myComponent.add(myControl.getComponent());
    myGradleSettingsInitialised = true;
  }
}
