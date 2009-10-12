/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.profile.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

/**
 * User: anna
 * Date: 01-Dec-2005
 */
public abstract class ProfileUIFactory implements ApplicationComponent {
  public static ProfileUIFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(ProfileUIFactory.class);
  }

  public abstract AbstractProfileMapping createAbstractProfileMapping(String profileType,
                                                                      String profileColumnTitle,
                                                                      String scopeColumnTitle,
                                                                      Project project,
                                                                      Condition<String> openEditProfilesDialog,
                                                                      List<Computable<DefaultMutableTreeNode>> additionalNodes);
}
