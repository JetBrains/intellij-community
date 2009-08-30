package com.intellij.refactoring.listeners;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author yole
 */
public abstract class JavaRefactoringListenerManager {
  /**
   * Registers a listener for moving member by pull up, push down and extract super class/interface refactorings.
   * @param moveMembersListener listener to register
   */
  public abstract void addMoveMembersListener(MoveMemberListener moveMembersListener);

  /**
   * Unregisters a previously registered listener.
   * @param moveMembersListener listener to unregister
   */
  public abstract void removeMoveMembersListener(MoveMemberListener moveMembersListener);

  public static JavaRefactoringListenerManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaRefactoringListenerManager.class);
  }
}
