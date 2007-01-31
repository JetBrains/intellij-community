/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionRVContentProvider;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class OfflineInspectionRVContentProvider extends InspectionRVContentProvider {
  private Map<String, Map<String, Set<OfflineProblemDescriptor>>> myContent;

  public OfflineInspectionRVContentProvider(final Map<String, Map<String, Set<OfflineProblemDescriptor>>> content,
                                            final Project project) {
    super(project);
    myContent = content;
  }

  public boolean checkReportedProblems(final InspectionTool tool) {
    return myContent.containsKey(tool.getShortName());
  }

  @Nullable
  public QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    final List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final Map<RefEntity, Set<QuickFix>> actions = new HashMap<RefEntity, Set<QuickFix>>();
    for (TreePath selectionPath : treePaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
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
                quickFixes.addAll(Arrays.asList(fixes));
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

    if (tool instanceof DescriptorProviderInspection) {
      return ((DescriptorProviderInspection)tool).extractActiveFixes(selectedRefElements, actions);
    }

    return tool.getQuickFixes(selectedRefElements);
  }

  public void appendToolNodeContent(final InspectionNode toolNode, final InspectionTreeNode parentNode, final boolean showStructure) {
    final InspectionTool tool = toolNode.getTool();
    final String shortName = tool.getShortName();
    if (myContent.containsKey(shortName)) {
      final Function<OfflineProblemDescriptor, UserObjectContainer<OfflineProblemDescriptor>> computeContainer =
        new Function<OfflineProblemDescriptor, UserObjectContainer<OfflineProblemDescriptor>>() {
          public UserObjectContainer<OfflineProblemDescriptor> fun(final OfflineProblemDescriptor descriptor) {
            return new OfflineProblemDescriptorContainer(descriptor);
          }
        };

      final List<InspectionTreeNode> list = buildTree(myContent.get(shortName), false, tool, computeContainer, showStructure);

      for (InspectionTreeNode node : list) {
        toolNode.add(node);
      }
      parentNode.add(toolNode);
    }
  }

  protected void appendDescriptor(final InspectionTool tool,
                                  final UserObjectContainer container,
                                  final InspectionPackageNode packageNode,
                                  final boolean canPackageRepeat) {
    final RefElementNode elemNode = addNodeToParent(container, tool, packageNode);
    if (tool instanceof DescriptorProviderInspection) {
      elemNode.add(new OfflineProblemDescriptorNode(((OfflineProblemDescriptorContainer)container).getUserObject(),
                                                    !(tool instanceof DuplicatePropertyInspection), (DescriptorProviderInspection)tool));
    }
  }


  private static class OfflineProblemDescriptorContainer implements UserObjectContainer<OfflineProblemDescriptor> {
    private OfflineProblemDescriptor myDescriptor;

    public OfflineProblemDescriptorContainer(final OfflineProblemDescriptor descriptor) {
      myDescriptor = descriptor;
    }

    @Nullable
    public OfflineProblemDescriptorContainer getOwner() {
      final OfflineProblemDescriptor descriptor = myDescriptor.getOwner();
      if (descriptor != null) {
        return new OfflineProblemDescriptorContainer(descriptor);
      }
      return null;
    }

    public RefElementNode createNode(InspectionTool tool) {
      return new OfflineRefElementNode(myDescriptor, tool);
    }

    public OfflineProblemDescriptor getUserObject() {
      return myDescriptor;
    }

    public String getModule() {
      return myDescriptor.getModuleName();
    }

    public boolean areEqual(final OfflineProblemDescriptor o1, final OfflineProblemDescriptor o2) {
      if (o1 == null || o2 == null) {
        return o1 == o2;
      }

      if (!Comparing.strEqual(o1.getFQName(), o2.getFQName())) return false;
      if (!Comparing.strEqual(o1.getType(), o2.getType())) return false;

      return true;
    }
  }
}