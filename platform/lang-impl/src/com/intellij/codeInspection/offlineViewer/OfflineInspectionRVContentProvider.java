/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Function;

public class OfflineInspectionRVContentProvider extends InspectionRVContentProvider {
  private final Map<String, Map<String, Set<OfflineProblemDescriptor>>> myContent;
  private final Map<String, Map<OfflineProblemDescriptor, OfflineDescriptorResolveResult>> myResolvedDescriptor =
    FactoryMap.createMap(key -> new THashMap<>());

  public OfflineInspectionRVContentProvider(@NotNull Map<String, Map<String, Set<OfflineProblemDescriptor>>> content,
                                            @NotNull Project project) {
    super(project);
    myContent = content;
  }

  @Override
  public boolean checkReportedProblems(@NotNull GlobalInspectionContextImpl context,
                                       @NotNull final InspectionToolWrapper toolWrapper) {
    final Map<String, Set<OfflineProblemDescriptor>> content = getFilteredContent(context, toolWrapper);
    return content != null && !content.values().isEmpty();
  }

  @Override
  public Iterable<? extends ScopeToolState> getTools(Tools tools) {
    return Collections.singletonList(tools.getDefaultState());
  }

  @NotNull
  @Override
  public QuickFixAction[] getQuickFixes(@NotNull final InspectionToolWrapper toolWrapper, @NotNull final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths == null) return QuickFixAction.EMPTY; 
    final List<RefEntity> selectedElements = new ArrayList<>();
    final Map<RefEntity, CommonProblemDescriptor[]> actions = new HashMap<>();
    for (TreePath selectionPath : treePaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), node -> {
        if (!((InspectionTreeNode)node).isValid()) return true;
        if (node instanceof OfflineProblemDescriptorNode) {
          if (((OfflineProblemDescriptorNode)node).isQuickFixAppliedFromView()) return true;
          final OfflineProblemDescriptorNode descriptorNode = (OfflineProblemDescriptorNode)node;
          final RefEntity element = descriptorNode.getElement();
          selectedElements.add(element);
          CommonProblemDescriptor[] descriptors = actions.get(element);
          final CommonProblemDescriptor descriptor = descriptorNode.getDescriptor();
          final CommonProblemDescriptor[] descriptorAsArray = descriptor == null ? CommonProblemDescriptor.EMPTY_ARRAY
                                                                                 : new CommonProblemDescriptor[]{descriptor};
          actions.put(element, descriptors == null ?
                               descriptorAsArray :
                               DefaultInspectionToolPresentation.mergeDescriptors(descriptors, descriptorAsArray));
        }
        else if (node instanceof RefElementNode) {
          selectedElements.add(((RefElementNode)node).getElement());
        }
        return true;
      });
    }

    if (selectedElements.isEmpty()) return QuickFixAction.EMPTY;

    final RefEntity[] selectedRefElements = selectedElements.toArray(new RefEntity[selectedElements.size()]);

    GlobalInspectionContextImpl context = tree.getContext();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    return presentation.extractActiveFixes(selectedRefElements, actions::get, tree.getSelectedDescriptors());
  }

  @Override
  public boolean isContentLoaded() {
    return false;
  }

  @Override
  public InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                              @NotNull InspectionNode toolNode,
                                              @NotNull InspectionTreeNode parentNode,
                                              boolean showStructure,
                                              boolean groupBySeverity, @NotNull final Map<String, Set<RefEntity>> contents,
                                              @NotNull Function<RefEntity, CommonProblemDescriptor[]> problems) {
    InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
    final Map<String, Set<OfflineProblemDescriptor>> filteredContent = getFilteredContent(context, toolWrapper);
    if (filteredContent != null && !filteredContent.values().isEmpty()) {
      parentNode.insertByOrder(toolNode, false);
      buildTree(context, filteredContent, false, toolWrapper, descriptor -> {
                  final RefEntity element = descriptor.getRefElement(context.getRefManager());
                  return new RefEntityContainer<OfflineProblemDescriptor>(element, new OfflineProblemDescriptor[] {descriptor}) {
                    @Nullable
                    @Override
                    public String getModule() {
                      final String module = super.getModule();
                      return module == null ? descriptor.getModuleName() : module;
                    }
                  };
                }, showStructure,
                (newChild) -> {
                  toolNode.insertByOrder(newChild, false);
                  return newChild;
                });
    }
    return toolNode;
  }

  @Nullable
  @SuppressWarnings({"UnusedAssignment"})
  private Map<String, Set<OfflineProblemDescriptor>> getFilteredContent(@NotNull GlobalInspectionContextImpl context,
                                                                        @NotNull InspectionToolWrapper toolWrapper) {
    Map<String, Set<OfflineProblemDescriptor>> content = myContent.get(toolWrapper.getShortName());
    if (content == null) return null;
    if (context.getUIOptions().FILTER_RESOLVED_ITEMS) {
      final Map<String, Set<OfflineProblemDescriptor>> current = new HashMap<>(content);
      content = null; //GC it
      InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
      for (RefEntity refEntity : presentation.getResolvedElements()) {
        if (refEntity instanceof RefElement) {
          excludeProblem(refEntity.getExternalName(), current);
        }
      }
      return current;
    }
    return content;
  }

  private static void excludeProblem(final String externalName, final Map<String, Set<OfflineProblemDescriptor>> content) {
    for (Iterator<String> iter = content.keySet().iterator(); iter.hasNext();) {
      final String packageName = iter.next();
      final Set<OfflineProblemDescriptor> excluded = new HashSet<>(content.get(packageName));
      for (Iterator<OfflineProblemDescriptor> it = excluded.iterator(); it.hasNext();) {
        final OfflineProblemDescriptor ex = it.next();
        if (Comparing.strEqual(ex.getFQName(), externalName)) {
          it.remove();
        }
      }
      if (excluded.isEmpty()) {
        iter.remove();
      } else {
        content.put(packageName, excluded);
      }
    }
  }

  @Override
  protected void appendDescriptor(@NotNull GlobalInspectionContextImpl context,
                                  @NotNull final InspectionToolWrapper toolWrapper,
                                  @NotNull final RefEntityContainer container,
                                  @NotNull final InspectionTreeNode packageNode,
                                  final boolean canPackageRepeat) {
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    final RefElementNode elemNode = addNodeToParent(container, presentation, packageNode);
    for (OfflineProblemDescriptor descriptor : ((RefEntityContainer<OfflineProblemDescriptor>)container).getDescriptors()) {
      final OfflineDescriptorResolveResult resolveResult = myResolvedDescriptor.get(toolWrapper.getShortName())
        .computeIfAbsent(descriptor, d -> OfflineDescriptorResolveResult.resolve(d, toolWrapper, presentation));
      elemNode.insertByOrder(ReadAction.compute(() -> OfflineProblemDescriptorNode.create(descriptor, resolveResult, presentation)), true);
    }
  }
}
