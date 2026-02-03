// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.listeners;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public abstract class JavaRefactoringListenerManager {
  /**
   * Registers a listener for moving member by pull up, push down and extract super class/interface refactorings.
   * @param moveMembersListener listener to register
   */
  public abstract void addMoveMembersListener(@NotNull MoveMemberListener moveMembersListener);

  /**
   * Unregisters a previously registered listener.
   * @param moveMembersListener listener to unregister
   */
  public abstract void removeMoveMembersListener(@NotNull MoveMemberListener moveMembersListener);

  public static JavaRefactoringListenerManager getInstance(Project project) {
    return project.getService(JavaRefactoringListenerManager.class);
  }
}
