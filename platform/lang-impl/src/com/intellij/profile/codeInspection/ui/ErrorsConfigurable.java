package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for the configurable which is used to configure the current inspection profile. 
 *
 * @author yole
 */
public interface ErrorsConfigurable extends Configurable {
  void selectProfile(final String name);
  void selectInspectionTool(final String selectedToolShortName);
  @Nullable
  Object getSelectedObject();

  class SERVICE {
    private SERVICE() {
    }

    @Nullable
    public static ErrorsConfigurable getInstance(Project project) {
      ErrorsConfigurable profileConfigurable = findErrorsConfigurable(project.getExtensions(PROJECT_CONFIGURABLES));
      if (profileConfigurable != null) return profileConfigurable;
      return findErrorsConfigurable(ApplicationManager.getApplication().getExtensions(APPLICATION_CONFIGURABLES));
    }

    @Nullable
    private static ErrorsConfigurable findErrorsConfigurable(final Configurable[] extensions) {
      for (Configurable configurable : extensions) {
        if (ErrorsConfigurable.class.isAssignableFrom(configurable.getClass())) {
          return (ErrorsConfigurable)configurable;
        }
      }
      return null;
    }
  }
}
