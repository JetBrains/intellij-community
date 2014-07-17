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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ArrayUtil;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

public class AddScopeUtil {
  public static ScopeToolState performAddScope(final TreeTable treeTable,
                                               final Project project,
                                               final InspectionProfileImpl inspectionProfile,
                                               final Collection<InspectionConfigTreeNode> selectedNodes) {
    final List<InspectionConfigTreeNode> nodes = new ArrayList<InspectionConfigTreeNode>();
    final List<Descriptor> descriptors = new ArrayList<Descriptor>();
    for (final InspectionConfigTreeNode node : selectedNodes) {
      collect(descriptors, nodes, node);
    }

    final List<String> availableScopes = getAvailableScopes(descriptors, project, inspectionProfile);
    final int idx = Messages.showChooseDialog(treeTable, "Scope:", "Choose Scope", ArrayUtil.toStringArray(availableScopes), availableScopes.get(0), Messages.getQuestionIcon());
    if (idx == -1) return null;
    final NamedScope chosenScope = NamedScopesHolder.getScope(project, availableScopes.get(idx));

    ScopeToolState scopeToolState = null;
    final Tree tree = treeTable.getTree();

    for (final InspectionConfigTreeNode node : nodes) {
      final Descriptor descriptor = node.getDefaultDescriptor();
      final InspectionToolWrapper toolWrapper = descriptor.getToolWrapper().createCopy(); //copy
      final HighlightDisplayLevel level = inspectionProfile.getErrorLevel(descriptor.getKey(), chosenScope, project);
      final boolean enabled = inspectionProfile.isToolEnabled(descriptor.getKey());
      scopeToolState = inspectionProfile.addScope(toolWrapper, chosenScope, level, enabled, project);
      node.dropCache();
      ((DefaultTreeModel)tree.getModel()).reload(node);
      tree.expandPath(new TreePath(node.getPath()));
    }
    tree.revalidate();
    return scopeToolState;
  }

  private static void collect(final List<Descriptor> descriptors,
                              final List<InspectionConfigTreeNode> nodes,
                              final InspectionConfigTreeNode node) {
    final ToolDescriptors currentDescriptors = node.getDescriptors();
    if (currentDescriptors != null) {
      nodes.add(node);
      descriptors.add(currentDescriptors.getDefaultDescriptor());
      descriptors.addAll(currentDescriptors.getNonDefaultDescriptors());
    } else if (node.getUserObject() instanceof String) {
      for(int i = 0; i < node.getChildCount(); i++) {
        final InspectionConfigTreeNode childNode = (InspectionConfigTreeNode)node.getChildAt(i);
        collect(descriptors, nodes, childNode);
      }
    }
  }

  private static List<String> getAvailableScopes(final List<Descriptor> descriptors, final Project project, final InspectionProfileImpl inspectionProfile) {
    final ArrayList<NamedScope> scopes = new ArrayList<NamedScope>();
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
      Collections.addAll(scopes, holder.getScopes());
    }
    scopes.remove(CustomScopesProviderEx.getAllScope());

    CustomScopesProviderEx.filterNoSettingsScopes(project, scopes);

    final Set<NamedScope> used = new HashSet<NamedScope>();
    for (final Descriptor descriptor : descriptors) {
      final List<ScopeToolState> nonDefaultTools = inspectionProfile.getNonDefaultTools(descriptor.getKey().toString(), project);
      if (nonDefaultTools != null) {
        for (final ScopeToolState state : nonDefaultTools) {
          used.add(state.getScope(project));
        }
      }
    }
    scopes.removeAll(used);

    final List<String> availableScopes = new ArrayList<String>();
    for (final NamedScope scope : scopes) {
      availableScopes.add(scope.getName());
    }
    return availableScopes;
  }
}