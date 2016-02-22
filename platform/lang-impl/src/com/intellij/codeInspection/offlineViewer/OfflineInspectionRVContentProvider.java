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
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.SmartRefElementPointer;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class OfflineInspectionRVContentProvider extends InspectionRVContentProvider {
  private final Map<String, Map<String, Set<OfflineProblemDescriptor>>> myContent;

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

  @Override
  @Nullable
  public QuickFixAction[] getQuickFixes(@NotNull final InspectionToolWrapper toolWrapper, @NotNull final InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths == null) return QuickFixAction.EMPTY; 
    final List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final Map<RefEntity, CommonProblemDescriptor[]> actions = new HashMap<>();
    for (TreePath selectionPath : treePaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
        @Override
        public boolean accept(final Object node) {
          if (!((InspectionTreeNode)node).isValid()) return true;
          if (node instanceof OfflineProblemDescriptorNode) {
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
        }
      });
    }

    if (selectedElements.isEmpty()) return null;

    final RefEntity[] selectedRefElements = selectedElements.toArray(new RefEntity[selectedElements.size()]);

    GlobalInspectionContextImpl context = tree.getContext();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    return presentation.extractActiveFixes(selectedRefElements, actions, tree.getSelectedDescriptors());
  }

  @Override
  public boolean isContentLoaded() {
    return false;
  }

  @Override
  public void appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                    @NotNull final InspectionNode toolNode,
                                    @NotNull final InspectionTreeNode parentNode,
                                    final boolean showStructure,
                                    @NotNull final Map<String, Set<RefEntity>> contents,
                                    @NotNull final Map<RefEntity, CommonProblemDescriptor[]> problems,
                                    final DefaultTreeModel model) {
    InspectionToolWrapper toolWrapper = toolNode.getToolWrapper();
    final Map<String, Set<OfflineProblemDescriptor>> filteredContent = getFilteredContent(context, toolWrapper);
    if (filteredContent != null && !filteredContent.values().isEmpty()) {
      final Function<OfflineProblemDescriptor, UserObjectContainer<OfflineProblemDescriptor>> computeContainer =
        new Function<OfflineProblemDescriptor, UserObjectContainer<OfflineProblemDescriptor>>() {
          @Override
          public UserObjectContainer<OfflineProblemDescriptor> fun(final OfflineProblemDescriptor descriptor) {
            return new OfflineProblemDescriptorContainer(descriptor);
          }
        };
      final List<InspectionTreeNode> list = buildTree(context, filteredContent, false, toolWrapper, computeContainer, showStructure);
      for (InspectionTreeNode node : list) {
        toolNode.add(node);
      }
      parentNode.add(toolNode);
    }
  }

  @Nullable
  @SuppressWarnings({"UnusedAssignment"})
  private Map<String, Set<OfflineProblemDescriptor>> getFilteredContent(@NotNull GlobalInspectionContextImpl context,
                                                                        @NotNull InspectionToolWrapper toolWrapper) {
    Map<String, Set<OfflineProblemDescriptor>> content = myContent.get(toolWrapper.getShortName());
    if (content == null) return null;
    if (context.getUIOptions().FILTER_RESOLVED_ITEMS) {
      final Map<String, Set<OfflineProblemDescriptor>> current = new HashMap<String, Set<OfflineProblemDescriptor>>(content);
      content = null; //GC it
      InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
      for (RefEntity refEntity : presentation.getIgnoredRefElements()) {
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
      final Set<OfflineProblemDescriptor> excluded = new HashSet<OfflineProblemDescriptor>(content.get(packageName));
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
                                  @NotNull final UserObjectContainer container,
                                  @NotNull final InspectionPackageNode packageNode,
                                  final boolean canPackageRepeat) {
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    final RefElementNode elemNode = addNodeToParent(container, presentation, packageNode);
    if (toolWrapper instanceof LocalInspectionToolWrapper) {
      elemNode.add(new OfflineProblemDescriptorNode(((OfflineProblemDescriptorContainer)container).getUserObject(),
                                                    (LocalInspectionToolWrapper)toolWrapper, presentation));
    }
  }


  private static class OfflineProblemDescriptorContainer implements UserObjectContainer<OfflineProblemDescriptor> {
    @NotNull
    private final OfflineProblemDescriptor myDescriptor;

    public OfflineProblemDescriptorContainer(@NotNull OfflineProblemDescriptor descriptor) {
      myDescriptor = descriptor;
    }

    @Override
    @Nullable
    public OfflineProblemDescriptorContainer getOwner() {
      final OfflineProblemDescriptor descriptor = myDescriptor.getOwner();
      if (descriptor != null) {
        final OfflineProblemDescriptorContainer container = new OfflineProblemDescriptorContainer(descriptor);
        return container.supportStructure() ? container : null;
      }
      return null;
    }

    @NotNull
    @Override
    public RefElementNode createNode(@NotNull InspectionToolPresentation presentation) {
      return new OfflineRefElementNode(myDescriptor, presentation);
    }

    @Override
    @NotNull
    public OfflineProblemDescriptor getUserObject() {
      return myDescriptor;
    }

    @Override
    public String getModule() {
      return myDescriptor.getModuleName();
    }

    @Override
    public boolean areEqual(final OfflineProblemDescriptor o1, final OfflineProblemDescriptor o2) {
      if (o1 == null || o2 == null) {
        return o1 == o2;
      }

      if (!Comparing.strEqual(o1.getFQName(), o2.getFQName())) return false;
      if (!Comparing.strEqual(o1.getType(), o2.getType())) return false;

      return true;
    }

    @Override
    public boolean supportStructure() {
      return !Comparing.strEqual(myDescriptor.getType(), SmartRefElementPointer.MODULE) &&
             !Comparing.strEqual(myDescriptor.getType(), "package") &&
             !Comparing.strEqual(myDescriptor.getType(), SmartRefElementPointer.PROJECT);
    }
  }
}
