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
import com.intellij.codeInspection.ex.InspectionResultsViewProvider;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class OfflineInspectionResultsViewProvider implements InspectionResultsViewProvider {
  private Map<String, Map<String, List<OfflineProblemDescriptor>>> myContent;

  public OfflineInspectionResultsViewProvider(final Map<String, Map<String, List<OfflineProblemDescriptor>>> content) {
    myContent = content;
  }

  public boolean hasReportedProblems(final InspectionTool tool) {
    return myContent.containsKey(tool.getShortName());
  }

  public InspectionTreeNode[] getContents(final InspectionTool tool) {
    final String shortName = tool.getShortName();
    if (myContent.containsKey(shortName)) {
      final Map<String, List<OfflineProblemDescriptor>> package2Problems = myContent.get(shortName);
      final List<InspectionTreeNode> result = new ArrayList<InspectionTreeNode>();
      for (String packageName : package2Problems.keySet()) {
        final InspectionPackageNode pNode = new InspectionPackageNode(packageName);
        final List<OfflineProblemDescriptor> elements = package2Problems.get(packageName);
        for (OfflineProblemDescriptor descriptor : elements) {
          final OfflineRefElementNode elemNode = addNodeToParent(descriptor, tool, pNode);
          if (tool instanceof DescriptorProviderInspection) {
            final OfflineProblemDescriptorNode problemNode = new OfflineProblemDescriptorNode(descriptor,
                                                                                              !(tool instanceof DuplicatePropertyInspection),
                                                                                              (DescriptorProviderInspection)tool);
            elemNode.add(problemNode);
          }
        }
        if (pNode.getChildCount() > 0) result.add(pNode);
      }
      return result.toArray(new InspectionTreeNode[result.size()]);
    }
    return new InspectionTreeNode[0];
  }




  protected static OfflineRefElementNode addNodeToParent(OfflineProblemDescriptor descriptor,
                                                         final InspectionTool tool,
                                                         final InspectionTreeNode parentNode) {
    final Set<InspectionTreeNode> children = new HashSet<InspectionTreeNode>();
    TreeUtil.traverseDepth(parentNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        children.add((InspectionTreeNode)node);
        return true;
      }
    });
    OfflineRefElementNode nodeToAdd = new OfflineRefElementNode(descriptor, tool);
    boolean firstLevel = true;
    RefElementNode prevNode = null;
    while (true) {
      OfflineRefElementNode currentNode = firstLevel ? nodeToAdd : new OfflineRefElementNode(descriptor, tool);
      for (InspectionTreeNode node : children) {
        if (node instanceof OfflineRefElementNode) {
          final OfflineRefElementNode refElementNode = (OfflineRefElementNode)node;
          if (Comparing.equal(refElementNode.getUserObject(), descriptor)) {
            if (firstLevel) {
              return refElementNode;
            }
            else {
              for (int i = 0 ; i < refElementNode.getChildCount(); i++) {
                if (Comparing.equal(((InspectionTreeNode)refElementNode.getChildAt(i)).getUserObject(),
                                    prevNode.getUserObject())) {
                  return nodeToAdd;
                }
              }
              refElementNode.add(prevNode);
              return nodeToAdd;
            }
          }
        }
      }
      if (!firstLevel) {
        currentNode.add(prevNode);
      }
      OfflineProblemDescriptor owner = descriptor.getOwner();
      if (owner == null) {
        parentNode.add(currentNode);
        return nodeToAdd;
      }
      descriptor = owner;
      prevNode = currentNode;
      firstLevel = false;
    }
  }

  @Nullable
  public QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    final List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final Map<RefEntity, Set<QuickFix>> actions = new HashMap<RefEntity, Set<QuickFix>>();
    for (TreePath selectionPath : treePaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
        public boolean accept(final Object node) {
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
          } else if (node instanceof RefElementNode) {
            selectedElements.add(((RefElementNode)node).getElement());
          }
          return true;
        }
      });
    }

    final RefEntity[] selectedRefElements = selectedElements.toArray(new RefEntity[selectedElements.size()]);

    if (tool instanceof DescriptorProviderInspection) {
      return ((DescriptorProviderInspection)tool).extractActiveFixes(selectedRefElements, actions);
    }

    return tool.getQuickFixes(selectedRefElements);
  }
}