/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.intention;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

/**
 * Manager for intentions. All intentions must be registered here.
 */
public abstract class IntentionManager implements ProjectComponent {

  /**
   * Returns instance of <code>IntententionManager</code> for given project.
   * @param project
   * @return
   */
  public static IntentionManager getInstance(Project project){
    return project.getComponent(IntentionManager.class);
  }

  /**
   * Registers intention action.
   * @param action
   */
  public abstract void addAction(IntentionAction action);

  /**
   * Returns all registered intention actions.
   * @return array of registered actions.
   */
  public abstract IntentionAction[] getIntentionActions();

  public abstract void registerIntentionAndMetaData(IntentionAction action, String[] category);

  public abstract void registerIntentionAndMetaData(IntentionAction action, String[] category, String descriptionDirectoryName);
}
