/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  /**
   * @deprecated custom directory name causes problem with internationalization of inspection descriptions.
   */
  @Deprecated
  public abstract void registerIntentionAndMetaData(IntentionAction action, String[] category, String descriptionDirectoryName);
}
