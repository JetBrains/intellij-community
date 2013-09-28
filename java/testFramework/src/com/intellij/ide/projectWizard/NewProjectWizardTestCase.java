package com.intellij.ide.projectWizard;

import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;

import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 23.09.13
 */
public abstract class NewProjectWizardTestCase extends ProjectWizardTestCase {
  @Override
  protected AbstractProjectWizard createWizard(Project project, File directory) {
    return new NewProjectWizard(project, ModulesProvider.EMPTY_MODULES_PROVIDER, directory.getPath());
  }
}
