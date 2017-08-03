/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class InspectionRVContentProviderImpl extends InspectionRVContentProvider {
  public InspectionRVContentProviderImpl(final Project project) {
    super(project);
  }

  @Override
  public boolean checkReportedProblems(@NotNull GlobalInspectionContextImpl context,
                                       @NotNull final InspectionToolWrapper toolWrapper) {
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    presentation.updateContent();

    final SearchScope searchScope = context.getCurrentScope().toSearchScope();
    if (searchScope instanceof LocalSearchScope) {
      final Map<String, Set<RefEntity>> contents = presentation.getContent();
      final Map<RefEntity, CommonProblemDescriptor[]> problemElements = presentation.getProblemElements();
      for (Set<RefEntity> entities : contents.values()) {
        for (Iterator<RefEntity> iterator = entities.iterator(); iterator.hasNext(); ) {
          RefEntity entity = iterator.next();
          if (entity instanceof RefElement) {
            final PsiElement element = ((RefElement)entity).getElement();
            if (element != null) {
              final TextRange range = element.getTextRange();
              if (range != null && ((LocalSearchScope)searchScope).containsRange(element.getContainingFile(), range)) {
                continue;
              }
            }
          }
          problemElements.remove(entity);
          iterator.remove();
        }
      }
    }

    return presentation.hasReportedProblems();
  }

  @NotNull
  @Override
  public QuickFixAction[] getQuickFixes(@NotNull final InspectionToolWrapper toolWrapper, @NotNull final InspectionTree tree) {
    final RefEntity[] refEntities = tree.getSelectedElements();
    InspectionToolPresentation presentation = tree.getContext().getPresentation(toolWrapper);
    return refEntities.length == 0 ? QuickFixAction.EMPTY : presentation.getQuickFixes(refEntities, tree);
  }


  @Override
  public InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                                  @NotNull final InspectionNode toolNode,
                                                  @NotNull final InspectionTreeNode parentNode,
                                                  final boolean showStructure,
                                                  boolean groupBySeverity,
                                                  @NotNull final Map<String, Set<RefEntity>> contents,
                                                  @NotNull final Map<RefEntity, CommonProblemDescriptor[]> problems) {
    final InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
    InspectionNode mergedToolNode = (InspectionNode)merge(toolNode, parentNode, !groupBySeverity);

    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    final Set<RefModule> moduleProblems = presentation.getModuleProblems();
    if (!moduleProblems.isEmpty()) {
      Set<RefEntity> entities = contents.get("");
      if (entities == null) {
        entities = new HashSet<>();
        contents.put("", entities);
      }
      entities.addAll(moduleProblems);
    }
    buildTree(context,
              contents,
              false,
              toolWrapper,
              refElement -> new RefEntityContainer<>(refElement, problems.get(refElement)),
              showStructure,
              node -> merge(node, mergedToolNode, true));
    return mergedToolNode;
  }

  @Override
  protected void appendDescriptor(@NotNull GlobalInspectionContextImpl context,
                                  @NotNull final InspectionToolWrapper toolWrapper,
                                  @NotNull final RefEntityContainer container,
                                  @NotNull final InspectionTreeNode pNode,
                                  final boolean canPackageRepeat) {
    final RefEntity refElement = container.getRefEntity();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    final CommonProblemDescriptor[] problems = ((RefEntityContainer<CommonProblemDescriptor>)container).getDescriptors();
    if (problems != null) {
        final RefElementNode elemNode = addNodeToParent(container, presentation, pNode);
        for (CommonProblemDescriptor problem : problems) {
          assert problem != null;
          elemNode
            .insertByOrder(ReadAction.compute(() -> new ProblemDescriptionNode(refElement, problem, presentation)), true);
          elemNode.setProblem(elemNode.getChildCount() == 1 ? problems[0] : null);
        }
    }
    else {
      if (canPackageRepeat && pNode instanceof InspectionPackageNode) {
        final Set<RefEntity> currentElements = presentation.getContent().get(((InspectionPackageNode) pNode).getPackageName());
        if (currentElements != null) {
          final Set<RefEntity> currentEntities = new HashSet<>(currentElements);
          if (RefUtil.contains(refElement, currentEntities)) return;
        }
      }
      addNodeToParent(container, presentation, pNode);
    }
  }
}
