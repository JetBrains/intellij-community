package com.intellij.ide.projectWizard;

import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 23.09.13
 */
public class NewProjectWizardTestCase extends ProjectWizardTestCase {
  @Override
  protected AbstractProjectWizard<? extends Step> createWizard(Project project, File directory) {
    return new NewProjectWizard(project, directory.getPath());
  }
}
