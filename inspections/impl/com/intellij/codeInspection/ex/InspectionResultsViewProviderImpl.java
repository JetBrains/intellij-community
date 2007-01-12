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
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionResultsViewProviderImpl extends InspectionResultsViewProvider {

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

  private static void buildTreeNode(final InspectionTool tool,
                                    final List<InspectionTreeNode> content,
                                    final Map<String, Set<RefElement>> packageContents,
                                    final Map<RefEntity, CommonProblemDescriptor[]> problemElements) {
    final GlobalInspectionContextImpl context = tool.getContext();
    Set<String> packages = packageContents.keySet();
    for (String packageName : packages) {
      final InspectionPackageNode pNode = new InspectionPackageNode(packageName);
      final Set<RefElement> elements = packageContents.get(packageName);
      for (RefElement refElement : elements) {
        final RefElementDescriptor descriptor = new RefElementDescriptor(refElement);
        if (context.getUIOptions().SHOW_ONLY_DIFF && tool.getElementStatus(refElement) == FileStatus.NOT_CHANGED) continue;
        if (tool instanceof DescriptorProviderInspection) {
          final DescriptorProviderInspection descriptorProviderInspection = (DescriptorProviderInspection)tool;
          final CommonProblemDescriptor[] problems = problemElements.get(refElement);
          if (problems != null) {
            final RefElementNode elemNode = addNodeToParent(descriptor, tool, pNode);
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
          addNodeToParent(descriptor, tool, pNode);
        }
      }
      if (pNode.getChildCount() > 0) content.add(pNode);
    }
  }

  private static class RefElementDescriptor implements Descriptor {
    private RefElement myElement;

    public RefElementDescriptor(final RefElement element) {
      myElement = element;
    }

    @Nullable
    public Descriptor getOwner() {
      final RefEntity entity = myElement.getOwner();
      if (entity instanceof RefElement){
        return new RefElementDescriptor((RefElement)entity);
      }
      return null;
    }

    public RefElementNode createNode(InspectionTool tool) {
      return new RefElementNode(myElement, tool);
    }

    public Object getUserObject() {
      return myElement;
    }
  }
}