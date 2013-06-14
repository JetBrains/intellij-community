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
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.RefDirectory;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InspectionRVContentProviderImpl extends InspectionRVContentProvider {

  public InspectionRVContentProviderImpl(final Project project) {
    super(project);
  }

  @Override
  public boolean checkReportedProblems(@NotNull final InspectionToolWrapper toolWrapper) {
    toolWrapper.updateContent();
    return toolWrapper.hasReportedProblems();
  }

  @Override
  @Nullable
  public QuickFixAction[] getQuickFixes(@NotNull final InspectionTool tool, @NotNull final InspectionTree tree) {
    final RefEntity[] refEntities = tree.getSelectedElements();
    return refEntities.length == 0 ? null : tool.getQuickFixes(refEntities);
  }


  @Override
  public void appendToolNodeContent(final InspectionNode toolNode,
                                    final InspectionTreeNode parentNode,
                                    final boolean showStructure,
                                    final Map<String, Set<RefEntity>> contents,
                                    final Map<RefEntity, CommonProblemDescriptor[]> problems,
                                    DefaultTreeModel model) {
    final InspectionTool tool = toolNode.getTool();

    Function<RefEntity, UserObjectContainer<RefEntity>> computeContainer = new Function<RefEntity, UserObjectContainer<RefEntity>>() {
      @Override
      public UserObjectContainer<RefEntity> fun(final RefEntity refElement) {
        return new RefElementContainer(refElement, problems != null ? problems.get(refElement) : null);
      }
    };

    final Set<RefModule> moduleProblems = tool.getModuleProblems();
    if (moduleProblems != null && !moduleProblems.isEmpty()) {
      Set<RefEntity> entities = contents.get("");
      if (entities == null) {
        entities = new HashSet<RefEntity>();
        contents.put("", entities);
      }
      entities.addAll(moduleProblems);
    }
    List<InspectionTreeNode> list = buildTree(contents, false, tool, computeContainer, showStructure);

    for (InspectionTreeNode node : list) {
      merge(model, node, toolNode, true);
    }

    if (tool.isOldProblemsIncluded()) {
      final Map<RefEntity, CommonProblemDescriptor[]> oldProblems =
        tool instanceof DescriptorProviderInspection && !(tool instanceof CommonInspectionToolWrapper)? ((DescriptorProviderInspection)tool)
          .getOldProblemElements() : null;
      computeContainer = new Function<RefEntity, UserObjectContainer<RefEntity>>() {
        @Override
        public UserObjectContainer<RefEntity> fun(final RefEntity refElement) {
          return new RefElementContainer(refElement, oldProblems != null ? oldProblems.get(refElement) : null);
        }
      };

      list = buildTree(tool.getOldContent(), true, tool, computeContainer, showStructure);

      for (InspectionTreeNode node : list) {
        merge(model, node, toolNode, true);
      }
    }
    merge(model, toolNode, parentNode, false);
  }

  @Override
  protected void appendDescriptor(@NotNull final InspectionTool tool,
                                  @NotNull final UserObjectContainer container,
                                  @NotNull final InspectionPackageNode pNode,
                                  final boolean canPackageRepeat) {
    final GlobalInspectionContextImpl context = tool.getContext();
    final RefElementContainer refElementDescriptor = (RefElementContainer)container;
    final RefEntity refElement = refElementDescriptor.getUserObject();
    if (context.getUIOptions().SHOW_ONLY_DIFF && tool.getElementStatus(refElement) == FileStatus.NOT_CHANGED) return;
    if (tool instanceof DescriptorProviderInspection && !(tool instanceof CommonInspectionToolWrapper)) {
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
        final Set<RefEntity> currentElements = tool.getContent().get(pNode.getPackageName());
        if (currentElements != null) {
          final Set<RefEntity> currentEntities = new HashSet<RefEntity>(currentElements);
          if (InspectionTool.contains(refElement, currentEntities)) return;
        }
      }
      addNodeToParent(container, tool, pNode);
    }
  }

  private static class RefElementContainer implements UserObjectContainer<RefEntity> {
    @NotNull
    private final RefEntity myElement;
    private final CommonProblemDescriptor[] myDescriptors;

    public RefElementContainer(@NotNull RefEntity element, CommonProblemDescriptor[] descriptors) {
      myElement = element;
      myDescriptors = descriptors;
    }

    @Override
    @Nullable
    public RefElementContainer getOwner() {
      final RefEntity entity = myElement.getOwner();
      if (entity instanceof RefElement) {
        return new RefElementContainer(entity, myDescriptors);
      }
      return null;
    }

    @NotNull
    @Override
    public RefElementNode createNode(@NotNull InspectionTool tool) {
      return new RefElementNode(myElement, tool);
    }

    @Override
    @NotNull
    public RefEntity getUserObject() {
      return myElement;
    }

    @Override
    @Nullable
    public String getModule() {
      final RefModule refModule = myElement instanceof RefElement
                                  ? ((RefElement)myElement).getModule()
                                  : myElement instanceof RefModule ? (RefModule)myElement : null;
      return refModule != null ? refModule.getName() : null;
    }

    @Override
    public boolean areEqual(final RefEntity o1, final RefEntity o2) {
      return Comparing.equal(o1, o2);
    }

    @Override
    public boolean supportStructure() {
      return myElement instanceof RefElement && !(myElement instanceof RefDirectory); //do not show structure for refModule and refPackage
    }

    public CommonProblemDescriptor[] getProblemDescriptors() {
      return myDescriptors;
    }
  }
}
