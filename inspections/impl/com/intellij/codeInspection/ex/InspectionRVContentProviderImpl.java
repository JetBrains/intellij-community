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
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionRVContentProviderImpl extends InspectionRVContentProvider {

  public InspectionRVContentProviderImpl(final Project project) {
    super(project);
  }

  public boolean hasReportedProblems(final InspectionTool tool) {
    tool.updateContent();
    return tool.hasReportedProblems();
  }

  @Nullable
  public QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    final List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    for (TreePath selectionPath : treePaths) {
      selectedElements.addAll(InspectionTree.getElementsToSuppressInSubTree((InspectionTreeNode)selectionPath.getLastPathComponent()));
    }
    return selectedElements.isEmpty() ? null : tool.getQuickFixes(selectedElements.toArray(new RefEntity[selectedElements.size()]));
  }

  public void appendToolNodeContent(final InspectionNode toolNode, final InspectionTreeNode parentNode, final boolean showStructure) {
    final InspectionTool tool = toolNode.getTool();
    final boolean runWithEditorProfile = tool.getContext().RUN_WITH_EDITOR_PROFILE;

    final Map<RefEntity, CommonProblemDescriptor[]> problems =
      tool instanceof DescriptorProviderInspection ? ((DescriptorProviderInspection)tool).getProblemElements() : null;
    Function<RefElement, UserObjectContainer<RefElement>> computeContainer = new Function<RefElement, UserObjectContainer<RefElement>>() {
      public UserObjectContainer<RefElement> fun(final RefElement refElement) {
        return new RefElementContainer(refElement, problems != null ? problems.get(refElement) : null);
      }
    };

    List<InspectionTreeNode> list = buildTree(tool.getPackageContent(), false, tool, computeContainer, showStructure);

    for (InspectionTreeNode node : list) {
      merge(node, toolNode, runWithEditorProfile);
    }

    if (tool.isOldProblemsIncluded()) {
      final Map<RefEntity, CommonProblemDescriptor[]> oldProblems =
        tool instanceof DescriptorProviderInspection ? ((DescriptorProviderInspection)tool)
          .getOldProblemElements() : null;
      computeContainer = new Function<RefElement, UserObjectContainer<RefElement>>() {
        public UserObjectContainer<RefElement> fun(final RefElement refElement) {
          return new RefElementContainer(refElement, oldProblems != null ? oldProblems.get(refElement) : null);
        }
      };

      list = buildTree(tool.getOldPackageContent(), true, tool, computeContainer, showStructure);

      for (InspectionTreeNode node : list) {
        merge(node, toolNode, true);
      }
    }
    merge(toolNode, parentNode, runWithEditorProfile);
  }

  protected void appendDescriptor(final InspectionTool tool,
                                  final UserObjectContainer container,
                                  final InspectionPackageNode pNode,
                                  final boolean canPackageRepeat) {
    final GlobalInspectionContextImpl context = tool.getContext();
    final RefElementContainer refElementDescriptor = ((RefElementContainer)container);
    final RefElement refElement = refElementDescriptor.getUserObject();
    if (context.getUIOptions().SHOW_ONLY_DIFF && tool.getElementStatus(refElement) == FileStatus.NOT_CHANGED) return;
    if (tool instanceof DescriptorProviderInspection) {
      final DescriptorProviderInspection descriptorProviderInspection = (DescriptorProviderInspection)tool;
      final CommonProblemDescriptor[] problems = refElementDescriptor.getProblemDescriptors();
      if (problems != null) {
        final RefElementNode elemNode = addNodeToParent(container, tool, pNode);
        for (CommonProblemDescriptor problem : problems) {
          if (context.getUIOptions().SHOW_ONLY_DIFF && descriptorProviderInspection.getProblemStatus(problem) == FileStatus.NOT_CHANGED) {
            continue;
          }
          elemNode.add(
            new ProblemDescriptionNode(refElement, problem, !(tool instanceof DuplicatePropertyInspection), descriptorProviderInspection));
          if (problems.length == 1) {
            elemNode.setProblem(problems[0]);
          }
        }
      }
    }
    else {
      if (canPackageRepeat) {
        final Set<RefElement> currentElements = tool.getPackageContent().get(pNode.getPackageName());
        if (currentElements != null) {
          final Set<RefEntity> currentEntities = new HashSet<RefEntity>(currentElements);
          if (InspectionTool.contains(refElement, currentEntities)) return;
        }
      }
      addNodeToParent(container, tool, pNode);
    }
  }

  private static class RefElementContainer implements UserObjectContainer<RefElement> {
    private RefElement myElement;
    private CommonProblemDescriptor[] myDescriptors;

    public RefElementContainer(final RefElement element, final CommonProblemDescriptor[] descriptors) {
      myElement = element;
      myDescriptors = descriptors;
    }

    @Nullable
    public RefElementContainer getOwner() {
      final RefEntity entity = myElement.getOwner();
      if (entity instanceof RefElement) {
        return new RefElementContainer((RefElement)entity, myDescriptors);
      }
      return null;
    }

    public RefElementNode createNode(InspectionTool tool) {
      return new RefElementNode(myElement, tool);
    }

    public RefElement getUserObject() {
      return myElement;
    }

    @Nullable
    public String getModule() {
      final RefModule refModule = myElement.getModule();
      return refModule != null ? refModule.getName() : null;
    }

    public boolean areEqual(final RefElement o1, final RefElement o2) {
      return Comparing.equal(o1, o2);
    }

    public CommonProblemDescriptor[] getProblemDescriptors() {
      return myDescriptors;
    }
  }
}
