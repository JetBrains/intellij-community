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

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.SmartRefElementPointer;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class OfflineInspectionRVContentProvider extends InspectionRVContentProvider {
  private final Map<String, Map<String, Set<OfflineProblemDescriptor>>> myContent;

  public OfflineInspectionRVContentProvider(@NotNull Map<String, Map<String, Set<OfflineProblemDescriptor>>> content,
                                            @NotNull Project project) {
    super(project);
    myContent = content;
  }

  @Override
  public boolean checkReportedProblems(@NotNull final InspectionToolWrapper toolWrapper) {
    final Map<String, Set<OfflineProblemDescriptor>> content = getFilteredContent(toolWrapper);
    return content != null && !content.values().isEmpty();
  }

  @Override
  @Nullable
  public QuickFixAction[] getQuickFixes(@NotNull final InspectionTool tool, @NotNull final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    final List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final Map<RefEntity, Set<QuickFix>> actions = new HashMap<RefEntity, Set<QuickFix>>();
    for (TreePath selectionPath : treePaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
        @Override
        public boolean accept(final Object node) {
          if (!((InspectionTreeNode)node).isValid()) return true;
          if (node instanceof OfflineProblemDescriptorNode) {
            final OfflineProblemDescriptorNode descriptorNode = (OfflineProblemDescriptorNode)node;
            final RefEntity element = descriptorNode.getElement();
            selectedElements.add(element);
            Set<QuickFix> quickFixes = actions.get(element);
            if (quickFixes == null) {
              quickFixes = new HashSet<QuickFix>();
              actions.put(element, quickFixes);
            }
            final CommonProblemDescriptor descriptor = descriptorNode.getDescriptor();
            if (descriptor != null) {
              final QuickFix[] fixes = descriptor.getFixes();
              if (fixes != null) {
                ContainerUtil.addAll(quickFixes, fixes);
              }
            }
          }
          else if (node instanceof RefElementNode) {
            selectedElements.add(((RefElementNode)node).getElement());
          }
          return true;
        }
      });
    }

    if (selectedElements.isEmpty()) return null;

    final RefEntity[] selectedRefElements = selectedElements.toArray(new RefEntity[selectedElements.size()]);

    if (tool instanceof DescriptorProviderInspection && !(tool instanceof CommonInspectionToolWrapper)) {
      return ((DescriptorProviderInspection)tool).extractActiveFixes(selectedRefElements, actions);
    }

    return tool.getQuickFixes(selectedRefElements);
  }

  @Override
  public boolean isContentLoaded() {
    return false;
  }

  @Override
  public void appendToolNodeContent(final InspectionNode toolNode,
                                    final InspectionTreeNode parentNode,
                                    final boolean showStructure,
                                    final Map<String, Set<RefEntity>> contents,
                                    final Map<RefEntity, CommonProblemDescriptor[]> problems,
                                    final DefaultTreeModel model) {
    final InspectionTool tool = toolNode.getTool();
    final Map<String, Set<OfflineProblemDescriptor>> filteredContent = getFilteredContent(tool);
    if (filteredContent != null && !filteredContent.values().isEmpty()) {
      final Function<OfflineProblemDescriptor, UserObjectContainer<OfflineProblemDescriptor>> computeContainer =
        new Function<OfflineProblemDescriptor, UserObjectContainer<OfflineProblemDescriptor>>() {
          @Override
          public UserObjectContainer<OfflineProblemDescriptor> fun(final OfflineProblemDescriptor descriptor) {
            return new OfflineProblemDescriptorContainer(descriptor);
          }
        };
      final List<InspectionTreeNode> list = buildTree(filteredContent, false, tool, computeContainer, showStructure);
      for (InspectionTreeNode node : list) {
        toolNode.add(node);
      }
      parentNode.add(toolNode);
    }
  }

  @Nullable
  @SuppressWarnings({"UnusedAssignment"})
  private Map<String, Set<OfflineProblemDescriptor>> getFilteredContent(@NotNull InspectionTool tool) {
    Map<String, Set<OfflineProblemDescriptor>> content = myContent.get(tool.getShortName());
    if (content == null) return null;
    if (tool.getContext().getUIOptions().FILTER_RESOLVED_ITEMS) {
      final Map<String, Set<OfflineProblemDescriptor>> current = new HashMap<String, Set<OfflineProblemDescriptor>>(content);
      content = null; //GC it
      for (RefEntity refEntity : tool.getIgnoredRefElements()) {
        if (refEntity instanceof RefElement) {
          excludeProblem(refEntity.getExternalName(), current);
        }
      }
      return current;
    }
    return content;
  }

  private static void excludeProblem(final String externalName, final Map<String, Set<OfflineProblemDescriptor>> content) {
    for (Iterator<String> iter = content.keySet().iterator(); iter.hasNext();) {
      final String packageName = iter.next();
      final Set<OfflineProblemDescriptor> excluded = new HashSet<OfflineProblemDescriptor>(content.get(packageName));
      for (Iterator<OfflineProblemDescriptor> it = excluded.iterator(); it.hasNext();) {
        final OfflineProblemDescriptor ex = it.next();
        if (Comparing.strEqual(ex.getFQName(), externalName)) {
          it.remove();
        }
      }
      if (excluded.isEmpty()) {
        iter.remove();
      } else {
        content.put(packageName, excluded);
      }
    }
  }

  @Override
  protected void appendDescriptor(@NotNull final InspectionTool tool,
                                  @NotNull final UserObjectContainer container,
                                  @NotNull final InspectionPackageNode packageNode,
                                  final boolean canPackageRepeat) {
    final RefElementNode elemNode = addNodeToParent(container, tool, packageNode);
    if (tool instanceof DescriptorProviderInspection && !(tool instanceof CommonInspectionToolWrapper)) {
      elemNode.add(new OfflineProblemDescriptorNode(((OfflineProblemDescriptorContainer)container).getUserObject(), (DescriptorProviderInspection)tool));
    }
  }


  private static class OfflineProblemDescriptorContainer implements UserObjectContainer<OfflineProblemDescriptor> {
    @NotNull
    private final OfflineProblemDescriptor myDescriptor;

    public OfflineProblemDescriptorContainer(@NotNull OfflineProblemDescriptor descriptor) {
      myDescriptor = descriptor;
    }

    @Override
    @Nullable
    public OfflineProblemDescriptorContainer getOwner() {
      final OfflineProblemDescriptor descriptor = myDescriptor.getOwner();
      if (descriptor != null) {
        final OfflineProblemDescriptorContainer container = new OfflineProblemDescriptorContainer(descriptor);
        return container.supportStructure() ? container : null;
      }
      return null;
    }

    @NotNull
    @Override
    public RefElementNode createNode(@NotNull InspectionTool tool) {
      return new OfflineRefElementNode(myDescriptor, tool);
    }

    @Override
    @NotNull
    public OfflineProblemDescriptor getUserObject() {
      return myDescriptor;
    }

    @Override
    public String getModule() {
      return myDescriptor.getModuleName();
    }

    @Override
    public boolean areEqual(final OfflineProblemDescriptor o1, final OfflineProblemDescriptor o2) {
      if (o1 == null || o2 == null) {
        return o1 == o2;
      }

      if (!Comparing.strEqual(o1.getFQName(), o2.getFQName())) return false;
      if (!Comparing.strEqual(o1.getType(), o2.getType())) return false;

      return true;
    }

    @Override
    public boolean supportStructure() {
      return !Comparing.strEqual(myDescriptor.getType(), SmartRefElementPointer.MODULE) &&
             !Comparing.strEqual(myDescriptor.getType(), "package") &&
             !Comparing.strEqual(myDescriptor.getType(), SmartRefElementPointer.PROJECT);
    }
  }
}
