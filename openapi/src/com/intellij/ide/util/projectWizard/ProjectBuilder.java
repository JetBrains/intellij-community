/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.project.Project;

public abstract class ProjectBuilder {
  public boolean isUpdate() {
    return false;
  }

  public abstract void commit(final Project project);

  public boolean validate(Project current, Project dest) {
    return true;
  }
  public void cleanup() {}

  public boolean isOpenProjectSettingsAfter() {
    return false;
  }
}