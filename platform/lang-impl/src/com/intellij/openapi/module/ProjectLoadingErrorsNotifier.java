package com.intellij.openapi.module;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class ProjectLoadingErrorsNotifier {

  public static ProjectLoadingErrorsNotifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectLoadingErrorsNotifier.class);
  }

  public abstract void registerError(ConfigurationErrorDescription errorDescription);

  public abstract void registerErrors(Collection<? extends ConfigurationErrorDescription> errorDescriptions);
}
