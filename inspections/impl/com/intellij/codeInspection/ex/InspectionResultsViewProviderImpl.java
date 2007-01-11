/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionResultsViewProviderImpl implements InspectionResultsViewProvider {

  public boolean hasReportedProblems(final InspectionTool tool) {
    tool.updateContent();
    return tool.hasReportedProblems();
  }

  public InspectionTreeNode[] getContents(final InspectionTool tool) {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    buildTreeNode(tool, content, tool.getPackageContent(),
                  tool instanceof DescriptorProviderInspection ? ((DescriptorProviderInspection)tool).getProblemElements() : null);
    if (tool.isOldProblemsIncluded()) {
      buildTreeNode(tool, content, tool.getOldPackageContent(),
                    tool instanceof DescriptorProviderInspection ? ((DescriptorProviderInspection)tool).getOldProblemElements() : null);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  @Nullable
  public QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    final List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    for (TreePath selectionPath : treePaths) {
      InspectionResultsView.traverseRefElements((InspectionTreeNode)selectionPath.getLastPathComponent(), selectedElements);
    }
    return tool.getQuickFixes(selectedElements.toArray(new RefEntity[selectedElements.size()]));
  }

  protected static RefElementNode addNodeToParent(RefElement refElement, InspectionTool tool, InspectionPackageNode packageNode) {
    final Set<InspectionTreeNode> children = new HashSet<InspectionTreeNode>();
    TreeUtil.traverseDepth(packageNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        children.add((InspectionTreeNode)node);
        return true;
      }
    });
    RefElementNode nodeToAdd = new RefElementNode(refElement, tool);
    boolean firstLevel = true;
    RefElementNode prevNode = null;
    while (true) {
      RefElementNode currentNode = firstLevel ? nodeToAdd : new RefElementNode(refElement, tool);
      for (InspectionTreeNode node : children) {
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          if (Comparing.equal(refElementNode.getElement(), refElement)) {
            if (firstLevel) {
              return refElementNode;
            }
            else {
              refElementNode.add(prevNode);
              return nodeToAdd;
            }
          }
        }
      }
      if (!firstLevel) {
        currentNode.add(prevNode);
      }
      RefEntity owner = refElement.getOwner();
      if (!(owner instanceof RefElement)) {
        packageNode.add(currentNode);
        return nodeToAdd;
      }
      refElement = (RefElement)owner;
      prevNode = currentNode;
      firstLevel = false;
    }
  }

  private static void buildTreeNode(final InspectionTool tool,
                                    final List<InspectionTreeNode> content,
                                    final Map<String, Set<RefElement>> packageContents,
                                    final Map<RefEntity, CommonProblemDescriptor[]> problemElements) {
    final GlobalInspectionContextImpl context = tool.getContext();
    Set<String> packages = packageContents.keySet();
    for (String packageName : packages) {
      InspectionPackageNode pNode = new InspectionPackageNode(packageName);
      Set<RefElement> elements = packageContents.get(packageName);
      for (RefElement refElement : elements) {
        if (context.getUIOptions().SHOW_ONLY_DIFF && tool.getElementStatus(refElement) == FileStatus.NOT_CHANGED) continue;
        if (tool instanceof DescriptorProviderInspection) {
          final DescriptorProviderInspection descriptorProviderInspection = (DescriptorProviderInspection)tool;
          final CommonProblemDescriptor[] problems = problemElements.get(refElement);
          if (problems != null) {
            final RefElementNode elemNode = addNodeToParent(refElement, tool, pNode);
            for (CommonProblemDescriptor problem : problems) {
              if (context.getUIOptions().SHOW_ONLY_DIFF && descriptorProviderInspection.getProblemStatus(problem) == FileStatus.NOT_CHANGED) {
                continue;
              }
              elemNode.add(new ProblemDescriptionNode(refElement, problem,
                                                      !(tool instanceof DuplicatePropertyInspection),
                                                      descriptorProviderInspection));
            }
            if (problems.length == 1) {
              elemNode.setProblem(problems[0]);
            }
          }
        }
        else {
          if (packageContents != tool.getPackageContent()) {
            final Set<RefElement> currentElements = tool.getPackageContent().get(packageName);
            if (currentElements != null) {
              final Set<RefEntity> currentEntities = new HashSet<RefEntity>(currentElements);
              if (InspectionTool.contains(refElement, currentEntities)) continue;
            }
          }
          addNodeToParent(refElement, tool, pNode);
        }
      }
      if (pNode.getChildCount() > 0) content.add(pNode);
    }
  }
}