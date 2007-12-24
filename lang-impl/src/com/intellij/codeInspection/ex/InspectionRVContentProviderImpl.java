/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionRVContentProviderImpl extends InspectionRVContentProvider {

  public InspectionRVContentProviderImpl(final Project project) {
    super(project);
  }

  public boolean checkReportedProblems(final InspectionTool tool) {
    tool.updateContent();
    return tool.hasReportedProblems();
  }

  @Nullable
  public QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree) {
    final RefEntity[] refEntities = tree.getSelectedElements();
    return refEntities.length == 0 ? null : tool.getQuickFixes(refEntities);
  }

  public void appendToolNodeContent(final InspectionNode toolNode, final InspectionTreeNode parentNode, final boolean showStructure) {
    final InspectionTool tool = toolNode.getTool();
    final boolean runWithEditorProfile = tool.getContext().RUN_WITH_EDITOR_PROFILE;

    final Map<RefEntity, CommonProblemDescriptor[]> problems =
      tool instanceof DescriptorProviderInspection ? ((DescriptorProviderInspection)tool).getProblemElements() : null;
    Function<RefEntity, UserObjectContainer<RefEntity>> computeContainer = new Function<RefEntity, UserObjectContainer<RefEntity>>() {
      public UserObjectContainer<RefEntity> fun(final RefEntity refElement) {
        return new RefElementContainer(refElement, problems != null ? problems.get(refElement) : null);
      }
    };

    final Map<String, Set<RefEntity>> contents = tool.getPackageContent();
    final Set<RefModule> moduleProblems = tool.getModuleProblems();
    if (moduleProblems != null) {
      Set<RefEntity> entities = contents.get("");
      if (entities == null) {
        entities = new HashSet<RefEntity>();
        contents.put("", entities);
      }
      entities.addAll(moduleProblems);
    }
    List<InspectionTreeNode> list = buildTree(contents, false, tool, computeContainer, showStructure);

    for (InspectionTreeNode node : list) {
      merge(node, toolNode, runWithEditorProfile);
    }

    if (tool.isOldProblemsIncluded()) {
      final Map<RefEntity, CommonProblemDescriptor[]> oldProblems =
        tool instanceof DescriptorProviderInspection ? ((DescriptorProviderInspection)tool)
          .getOldProblemElements() : null;
      computeContainer = new Function<RefEntity, UserObjectContainer<RefEntity>>() {
        public UserObjectContainer<RefEntity> fun(final RefEntity refElement) {
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
    final RefEntity refElement = refElementDescriptor.getUserObject();
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
            new ProblemDescriptionNode(refElement, problem, descriptorProviderInspection));
          if (problems.length == 1) {
            elemNode.setProblem(problems[0]);
          }
        }
      }
    }
    else {
      if (canPackageRepeat) {
        final Set<RefEntity> currentElements = tool.getPackageContent().get(pNode.getPackageName());
        if (currentElements != null) {
          final Set<RefEntity> currentEntities = new HashSet<RefEntity>(currentElements);
          if (InspectionTool.contains(refElement, currentEntities)) return;
        }
      }
      addNodeToParent(container, tool, pNode);
    }
  }

  private static class RefElementContainer implements UserObjectContainer<RefEntity> {
    private RefEntity myElement;
    private CommonProblemDescriptor[] myDescriptors;

    public RefElementContainer(final RefEntity element, final CommonProblemDescriptor[] descriptors) {
      myElement = element;
      myDescriptors = descriptors;
    }

    @Nullable
    public RefElementContainer getOwner() {
      final RefEntity entity = myElement.getOwner();
      if (entity instanceof RefElement) {
        return new RefElementContainer(entity, myDescriptors);
      }
      return null;
    }

    public RefElementNode createNode(InspectionTool tool) {
      return new RefElementNode(myElement, tool);
    }

    public RefEntity getUserObject() {
      return myElement;
    }

    @Nullable
    public String getModule() {
      final RefModule refModule = myElement instanceof RefElement
                                  ? ((RefElement)myElement).getModule()
                                  : myElement instanceof RefModule ? ((RefModule)myElement) : null;
      return refModule != null ? refModule.getName() : null;
    }

    public boolean areEqual(final RefEntity o1, final RefEntity o2) {
      return Comparing.equal(o1, o2);
    }

    public boolean supportStructure() {
      return myElement instanceof RefElement; //do not show structure for refModule and refPackage
    }

    public CommonProblemDescriptor[] getProblemDescriptors() {
      return myDescriptors;
    }
  }
}
