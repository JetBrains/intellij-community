package com.intellij.pom;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.references.PomReferenceService;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PomManager {
  private PomManager() {
  }

  @NotNull
  public static PomModel getModel(Project project) {
    return ServiceManager.getService(project, PomModel.class);
  }

  @NotNull
  public static PomReferenceService getReferenceService() {
    return ServiceManager.getService(PomReferenceService.class);
  }
}