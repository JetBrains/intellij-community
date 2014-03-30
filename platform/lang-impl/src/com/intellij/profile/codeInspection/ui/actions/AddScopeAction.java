/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui.actions;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.profile.codeInspection.ui.InspectionConfigTreeNode;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

public abstract class AddScopeAction extends AnAction {
  private final Tree myTree;
  private static final Logger LOG = Logger.getInstance("#" + AddScopeAction.class.getName());

  public AddScopeAction(Tree tree) {
    super("Add Scope", "Add Scope", IconUtil.getAddIcon());
    myTree = tree;
    registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (getSelectedProfile() == null) return;
    final Project project = getProject(e);
    final InspectionConfigTreeNode[] selectedNodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    if (selectedNodes == null) return;
    final List<Descriptor> descriptors = new ArrayList<Descriptor>();
    for (InspectionConfigTreeNode node : selectedNodes) {
      collect(descriptors, new ArrayList<InspectionConfigTreeNode>(), node);
    }

    presentation.setEnabled(!getAvailableScopes(project, descriptors).isEmpty());
  }

  private static Project getProject(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    return project;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final List<Descriptor> descriptors = new ArrayList<Descriptor>();
    final InspectionConfigTreeNode[] selectedNodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    LOG.assertTrue(selectedNodes != null);

    final List<InspectionConfigTreeNode> nodes = new ArrayList<InspectionConfigTreeNode>(Arrays.asList(selectedNodes));
    for (InspectionConfigTreeNode node : selectedNodes) {
      collect(descriptors, nodes, node);
    }

    final Project project = getProject(e);
    final List<String> availableScopes = getAvailableScopes(project, descriptors);
    final int idx = Messages.showChooseDialog(myTree, "Scope:", "Choose Scope", ArrayUtil.toStringArray(availableScopes), availableScopes.get(0), Messages.getQuestionIcon());
    if (idx == -1) return;
    final NamedScope chosenScope = NamedScopesHolder.getScope(project, availableScopes.get(idx));

    for (InspectionConfigTreeNode node : nodes) {
      final Descriptor descriptor = node.getDescriptor();
      if (node.getScopeName() != null || descriptor == null) continue;
      final InspectionToolWrapper toolWrapper = descriptor.getToolWrapper(); //copy
      InspectionProfileImpl selectedProfile = getSelectedProfile();
      HighlightDisplayLevel level = selectedProfile.getErrorLevel(descriptor.getKey(), chosenScope, project);
      boolean enabled = selectedProfile.isToolEnabled(descriptor.getKey());
      final ScopeToolState scopeToolState = selectedProfile.addScope(toolWrapper, chosenScope, level, enabled, project);
      final Descriptor addedDescriptor = new Descriptor(scopeToolState, selectedProfile, project);
      if (node.getChildCount() == 0) {
        node.add(new InspectionConfigTreeNode(descriptor, selectedProfile.getToolDefaultState(descriptor.getKey().toString(), project), true, true, false));
      }
      node.insert(new InspectionConfigTreeNode(addedDescriptor, scopeToolState, false, false), 0);
      node.setInspectionNode(false);
      node.dropCache();
      ((DefaultTreeModel)myTree.getModel()).reload(node);
      myTree.expandPath(new TreePath(node.getPath()));
    }
    myTree.revalidate();
  }

  private static void collect(List<Descriptor> descriptors,
                              List<InspectionConfigTreeNode> nodes,
                              InspectionConfigTreeNode node) {
    final Descriptor descriptor = node.getDescriptor();
    if (descriptor != null) {
      if (node.getScopeName() == null) {
        descriptors.add(descriptor);
      }
    } else if (node.getUserObject() instanceof String) {
      for(int i = 0; i < node.getChildCount(); i++) {
        final InspectionConfigTreeNode childNode = (InspectionConfigTreeNode)node.getChildAt(i);
        nodes.add(childNode);
        collect(descriptors, nodes, childNode);
      }
    }
  }

  private List<String> getAvailableScopes(Project project, List<Descriptor> descriptors) {
    final ArrayList<NamedScope> scopes = new ArrayList<NamedScope>();
    for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
      Collections.addAll(scopes, holder.getScopes());
    }
    scopes.remove(CustomScopesProviderEx.getAllScope());

    CustomScopesProviderEx.filterNoSettingsScopes(project, scopes);

    final Set<NamedScope> used = new HashSet<NamedScope>();
    for (Descriptor descriptor : descriptors) {
      final List<ScopeToolState> nonDefaultTools = getSelectedProfile().getNonDefaultTools(descriptor.getKey().toString(), project);
      if (nonDefaultTools != null) {
        for (ScopeToolState state : nonDefaultTools) {
          used.add(state.getScope(project));
        }
      }
    }
    scopes.removeAll(used);

    final List<String> availableScopes = new ArrayList<String>();
    for (NamedScope scope : scopes) {
      availableScopes.add(scope.getName());
    }
    return availableScopes;
  }

  protected abstract InspectionProfileImpl getSelectedProfile();
}