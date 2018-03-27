/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class InspectionRVContentProviderImpl extends InspectionRVContentProvider {
  public InspectionRVContentProviderImpl(final Project project) {
    super(project);
  }

  @Override
  public boolean checkReportedProblems(@NotNull GlobalInspectionContextImpl context,
                                       @NotNull final InspectionToolWrapper toolWrapper) {
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    presentation.updateContent();

    AnalysisScope scope = context.getCurrentScope();
    if (scope == null) return false;
    final SearchScope searchScope = scope.toSearchScope();
    if (searchScope instanceof LocalSearchScope) {
      final Map<String, Set<RefEntity>> contents = presentation.getContent();
      final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> problemElements = presentation.getProblemElements();
      for (Set<RefEntity> entities : contents.values()) {
        for (Iterator<RefEntity> iterator = entities.iterator(); iterator.hasNext();) {
          RefEntity entity = iterator.next();
          if (entity instanceof RefElement) {
            SmartPsiElementPointer pointer = ((RefElement)entity).getPointer();
            if (pointer != null) {
              VirtualFile vFile = pointer.getVirtualFile();
              if (vFile != null && searchScope.contains(vFile)) {
                final PsiElement element = ((RefElement)entity).getElement();
                if (element != null) {
                  final TextRange range = element.getTextRange();
                  if (range != null && ((LocalSearchScope)searchScope).containsRange(element.getContainingFile(), range)) {
                    continue;
                  }
                }
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
  public QuickFixAction[] getCommonQuickFixes(@NotNull final InspectionToolWrapper toolWrapper, @NotNull final InspectionTree tree) {
    InspectionToolPresentation presentation = tree.getContext().getPresentation(toolWrapper);
    QuickFixAction[] fixes = getCommonFixes(presentation, tree.getSelectedDescriptors());
    return ArrayUtil.mergeArrays(fixes, presentation.getQuickFixes(tree.getSelectedElements()), QuickFixAction[]::new);
  }

  @Override
  public InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                                  @NotNull final InspectionNode toolNode,
                                                  @NotNull final InspectionTreeNode parentNode,
                                                  final boolean showStructure,
                                                  boolean groupBySeverity,
                                                  @NotNull final Map<String, Set<RefEntity>> contents,
                                                  @NotNull final Function<RefEntity, CommonProblemDescriptor[]> problems) {
    final InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
    InspectionNode mergedToolNode = (InspectionNode)merge(toolNode, parentNode, !groupBySeverity);

    buildTree(context,
              contents,
              false,
              toolWrapper,
              refElement -> new RefEntityContainer<>(refElement, problems.apply(refElement)),
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
    if (problems != null && problems.length != 0) {
        final RefElementNode elemNode = addNodeToParent(container, presentation, pNode);
        for (CommonProblemDescriptor problem : problems) {
          assert problem != null;
          elemNode.insertByOrder(ReadAction.compute(() -> new ProblemDescriptionNode(refElement, problem, presentation)), false);
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
