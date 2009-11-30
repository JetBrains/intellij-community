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

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.actions.MoveModulesToGroupAction;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ModuleGroupNode extends ProjectViewNode<ModuleGroup> implements DropTargetNode {
  public ModuleGroupNode(final Project project, final ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }
   public ModuleGroupNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (ModuleGroup)value, viewSettings);
  }

  protected abstract AbstractTreeNode createModuleNode(Module module) throws
                                                                      InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;
  protected abstract ModuleGroupNode createModuleGroupNode(ModuleGroup moduleGroup);

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final Collection<ModuleGroup> childGroups = getValue().childGroups(getProject());
    final List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (final ModuleGroup childGroup : childGroups) {
      result.add(createModuleGroupNode(childGroup));
    }
    Collection<Module> modules = getValue().modulesInGroup(getProject(), false);
    try {
      for (Module module : modules) {
        result.add(createModuleNode(module));
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }

    return result;
  }

  public boolean contains(@NotNull VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    final String[] groupPath = getValue().getGroupPath();
    presentation.setPresentableText(groupPath[groupPath.length-1]);
    presentation.setOpenIcon(Icons.OPENED_MODULE_GROUP_ICON);
    presentation.setClosedIcon(Icons.CLOSED_MODULE_GROUP_ICON);
  }

  public String getTestPresentation() {
    return "Group: " + getValue();
  }

  public String getToolTip() {
    return IdeBundle.message("tooltip.module.group");
  }

  public int getWeight() {
    return 0;
  }

  public int getTypeSortWeight(final boolean sortByType) {
    return 1;
  }

  public boolean canDrop(TreeNode[] sourceNodes) {
    final List<Module> modules = extractModules(sourceNodes);
    return !modules.isEmpty();
  }

  public void drop(TreeNode[] sourceNodes) {
    final List<Module> modules = extractModules(sourceNodes);
    MoveModulesToGroupAction.doMove(modules.toArray(new Module[modules.size()]), getValue(), null);
  }

  private static List<Module> extractModules(TreeNode[] sourceNodes) {
    final List<Module> modules = new ArrayList<Module>();
    for (TreeNode sourceNode : sourceNodes) {
      if (sourceNode instanceof DefaultMutableTreeNode) {
        final Object userObject = AbstractProjectViewPane.extractUserObject((DefaultMutableTreeNode)sourceNode);
        if (userObject instanceof Module) {
          modules.add((Module) userObject);
        }
      }
    }
    return modules;
  }
}
