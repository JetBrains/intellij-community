/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;

/**
 * @author max
 */
public class InspectionRootNode extends InspectionTreeNode {
  private static final Icon APP_ICON = PlatformUtils.isJetBrainsProduct()
                                       ? AllIcons.Nodes.IdeaProject
                                       : IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());
  private final Project myProject;

  public InspectionRootNode(Project project, @NotNull InspectionTreeUpdater updater) {
    super(project);
    myProject = project;
    myUpdater = updater;
  }

  public String toString() {
    return isEmpty() ? InspectionsBundle.message("inspection.empty.root.node.text") :
           myProject.getName();
  }

  private boolean isEmpty() {
    return getChildCount() == 0;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return APP_ICON;
  }
}
