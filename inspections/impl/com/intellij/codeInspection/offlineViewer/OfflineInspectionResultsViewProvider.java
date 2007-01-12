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
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class OfflineInspectionResultsViewProvider extends InspectionResultsViewProvider {
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
          final RefElementNode elemNode = addNodeToParent(new OfflineDescriptor(descriptor), tool, pNode);
          if (tool instanceof DescriptorProviderInspection) {
            elemNode.add(new OfflineProblemDescriptorNode(descriptor,
                                                          !(tool instanceof DuplicatePropertyInspection),
                                                          (DescriptorProviderInspection)tool));
          }
        }
        if (pNode.getChildCount() > 0) result.add(pNode);
      }
      return result.toArray(new InspectionTreeNode[result.size()]);
    }
    return new InspectionTreeNode[0];
  }


   private static class OfflineDescriptor implements Descriptor {
     private OfflineProblemDescriptor myDescriptor;

     public OfflineDescriptor(final OfflineProblemDescriptor descriptor) {
       myDescriptor = descriptor;
     }

     @Nullable
     public Descriptor getOwner() {
       final OfflineProblemDescriptor descriptor = myDescriptor.getOwner();
       if (descriptor != null) {
         return new OfflineDescriptor(descriptor);
       }
       return null;
     }

     public RefElementNode createNode(InspectionTool tool) {
       return new OfflineRefElementNode(myDescriptor, tool);
     }

     public Object getUserObject() {
       return myDescriptor;
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