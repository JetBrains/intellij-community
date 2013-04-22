package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectConfigurable;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MessageType;
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

  @NotNull private AbstractExternalProjectConfigurable myConfigurable;

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
    AbstractExternalProjectConfigurable.ValidationError error = myConfigurable.validate();
    if (error != null) {
      ExternalSystemUiUtil.showBalloon(error.problemHolder, MessageType.ERROR, error.message);
    }
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
    if (myConfigurable.isModified()) {
      myConfigurable.apply();
    }
    final String projectPath = myConfigurable.getLinkedExternalProjectPath();
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
    myConfigurable = builder.getConfigurable();
    myComponent.add(myConfigurable.createComponent());
    myGradleSettingsInitialised = true;
  }
}
