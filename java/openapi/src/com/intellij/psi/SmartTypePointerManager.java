/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class SmartTypePointerManager {
  public static SmartTypePointerManager getInstance(Project project) {
    return ServiceManager.getService(project, SmartTypePointerManager.class);
  }

  @NotNull
  public abstract SmartTypePointer createSmartTypePointer(PsiType type);
}