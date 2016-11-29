/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

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

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final Collection<ModuleGroup> childGroups = getValue().childGroups(getProject());
    final List<AbstractTreeNode> result = new ArrayList<>();
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

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    Collection<AbstractTreeNode> children = getChildren();
    Set<VirtualFile> result = new HashSet<>();
    for (AbstractTreeNode each : children) {
      if (each instanceof ProjectViewNode) {
        result.addAll(((ProjectViewNode)each).getRoots());
      }
    }

    return result;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return someChildContainsFile(file, false);
  }

  @Override
  public void update(PresentationData presentation) {
    final String[] groupPath = getValue().getGroupPath();
    presentation.setPresentableText(groupPath[groupPath.length-1]);
    presentation.setIcon(PlatformIcons.CLOSED_MODULE_GROUP_ICON);
  }

  @Override
  public String getTestPresentation() {
    return "Group: " + getValue();
  }

  @Override
  public String getToolTip() {
    return IdeBundle.message("tooltip.module.group");
  }

  @Override
  public int getWeight() {
    return 0;
  }

  @Override
  public int getTypeSortWeight(final boolean sortByType) {
    return 1;
  }

  @Override
  public boolean canDrop(TreeNode[] sourceNodes) {
    final List<Module> modules = extractModules(sourceNodes);
    return !modules.isEmpty();
  }

  @Override
  public void drop(TreeNode[] sourceNodes, DataContext dataContext) {
    final List<Module> modules = extractModules(sourceNodes);
    MoveModulesToGroupAction.doMove(modules.toArray(new Module[modules.size()]), getValue(), null);
  }

  @Override
  public void dropExternalFiles(PsiFileSystemItem[] sourceFileArray, DataContext dataContext) {
    // Do nothing, N/A
  }

  private static List<Module> extractModules(TreeNode[] sourceNodes) {
    final List<Module> modules = new ArrayList<>();
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
