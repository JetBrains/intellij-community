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

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.offlineViewer.OfflineRefElementNode;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.UnaryOperator;

public abstract class InspectionRVContentProvider {
  private static final Logger LOG = Logger.getInstance("#" + InspectionRVContentProvider.class.getName());
  private final Project myProject;

  public InspectionRVContentProvider(@NotNull Project project) {
    myProject = project;
  }

  protected interface UserObjectContainer<T> {
    @Nullable
    UserObjectContainer<T> getOwner();

    @NotNull
    RefElementNode createNode(@NotNull InspectionToolPresentation presentation);

    @NotNull
    T getUserObject();

    @Nullable
    String getModule();

    boolean areEqual(final T o1, final T o2);

    boolean supportStructure();
  }

  public abstract boolean checkReportedProblems(@NotNull GlobalInspectionContextImpl context, @NotNull InspectionToolWrapper toolWrapper);

  public Iterable<? extends ScopeToolState> getTools(Tools tools) {
    return tools.getTools();
  }

  public boolean hasQuickFixes(InspectionTree tree) {
    final TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths == null) return false;
    for (TreePath selectionPath : treePaths) {
      if (!TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), node -> {
        if (!((InspectionTreeNode) node).isValid()) return true;
        if (node instanceof ProblemDescriptionNode) {
          ProblemDescriptionNode problemDescriptionNode = (ProblemDescriptionNode)node;
          if (!problemDescriptionNode.isQuickFixAppliedFromView()) {
            final CommonProblemDescriptor descriptor = problemDescriptionNode.getDescriptor();
            final QuickFix[] fixes = descriptor != null ? descriptor.getFixes() : null;
            return fixes == null || fixes.length == 0;
          }
        }
        return true;
      })) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public abstract QuickFixAction[] getQuickFixes(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionTree tree);


  public InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                              @NotNull InspectionNode toolNode,
                                              @NotNull InspectionTreeNode parentNode,
                                              boolean showStructure,
                                              boolean groupBySeverity) {
    InspectionToolWrapper wrapper = toolNode.getToolWrapper();
    InspectionToolPresentation presentation = context.getPresentation(wrapper);
    Map<String, Set<RefEntity>> content = presentation.getContent();
    Map<RefEntity, CommonProblemDescriptor[]> problems = presentation.getProblemElements();
    return appendToolNodeContent(context, toolNode, parentNode, showStructure, groupBySeverity, content, problems);
  }

  public abstract InspectionNode appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                                           @NotNull InspectionNode toolNode,
                                                           @NotNull InspectionTreeNode parentNode,
                                                           final boolean showStructure,
                                                           boolean groupBySeverity,
                                                           @NotNull Map<String, Set<RefEntity>> contents,
                                                           @NotNull Map<RefEntity, CommonProblemDescriptor[]> problems);

  protected abstract void appendDescriptor(@NotNull GlobalInspectionContextImpl context,
                                           @NotNull InspectionToolWrapper toolWrapper,
                                           @NotNull UserObjectContainer container,
                                           @NotNull InspectionTreeNode pNode,
                                           final boolean canPackageRepeat);

  public boolean isContentLoaded() {
    return true;
  }

  protected <T> void buildTree(@NotNull GlobalInspectionContextImpl context,
                               @NotNull Map<String, Set<T>> packageContents,
                               final boolean canPackageRepeat,
                               @NotNull InspectionToolWrapper toolWrapper,
                               @NotNull Function<T, UserObjectContainer<T>> computeContainer,
                               final boolean showStructure,
                               final UnaryOperator<InspectionTreeNode> createdNodesConsumer) {
    final Map<String, Map<String, InspectionPackageNode>> module2PackageMap = new HashMap<String, Map<String, InspectionPackageNode>>();
    boolean supportStructure = showStructure;
    final MultiMap<InspectionPackageNode, UserObjectContainer<T>> packageDescriptors = new MultiMap<>();
    for (String packageName : packageContents.keySet()) {
      final Set<T> elements = packageContents.get(packageName);
      for (T userObject : elements) {
        final UserObjectContainer<T> container = computeContainer.fun(userObject);
        supportStructure &= container.supportStructure();
        final String moduleName = showStructure ? container.getModule() : null;
        Map<String, InspectionPackageNode> packageNodes = module2PackageMap.get(moduleName);
        if (packageNodes == null) {
          packageNodes = new HashMap<String, InspectionPackageNode>();
          module2PackageMap.put(moduleName, packageNodes);
        }
        InspectionPackageNode pNode = packageNodes.get(packageName);
        if (pNode == null) {
          pNode = new InspectionPackageNode(packageName);
          packageNodes.put(packageName, pNode);
        }

        packageDescriptors.putValue(pNode, container);
      }
    }

    if (supportStructure) {
      final HashMap<String, InspectionModuleNode> moduleNodes = new HashMap<String, InspectionModuleNode>();
      for (final String moduleName : module2PackageMap.keySet()) {
        final Map<String, InspectionPackageNode> packageNodes = module2PackageMap.get(moduleName);
        InspectionModuleNode moduleNode = moduleNodes.get(moduleName);

        if (moduleNode == null) {
          if (moduleName != null) {
            final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleName);
            if (module != null) {
              moduleNode = new InspectionModuleNode(module);
              moduleNodes.put(moduleName, moduleNode);
              moduleNode = (InspectionModuleNode)createdNodesConsumer.apply(moduleNode);
            }
            else { //module content was removed ?
              continue;
            }
          }
          else {
            for (InspectionPackageNode packageNode : packageNodes.values()) {
              createdNodesConsumer.apply(packageNode);
              for (UserObjectContainer<T> container : packageDescriptors.get(packageNode)) {
                appendDescriptor(context, toolWrapper, container, packageNode, canPackageRepeat);
              }
            }
            continue;
          }
        } else {
          moduleNode = (InspectionModuleNode)createdNodesConsumer.apply(moduleNode);
        }
        for (InspectionPackageNode packageNode : packageNodes.values()) {
          if (packageNode.getPackageName() != null) {
            Collection<UserObjectContainer<T>> objectContainers = packageDescriptors.get(packageNode);
            packageNode = (InspectionPackageNode)merge(packageNode, moduleNode, true);
            for (UserObjectContainer<T> container : objectContainers) {
              appendDescriptor(context, toolWrapper, container, packageNode, canPackageRepeat);
            }
          }
          else {
            for (UserObjectContainer<T> container : packageDescriptors.get(packageNode)) {
              appendDescriptor(context, toolWrapper, container, moduleNode, canPackageRepeat);
            }
          }
        }
      }
    }
    else {
      for (Map<String, InspectionPackageNode> packageNodes : module2PackageMap.values()) {
        for (InspectionPackageNode pNode : packageNodes.values()) {
          for (UserObjectContainer<T> container : packageDescriptors.get(pNode)) {
            appendDescriptor(context, toolWrapper, container, pNode, canPackageRepeat);
          }
          final int count = pNode.getChildCount();
          final ArrayList<TreeNode> childNodes = new ArrayList<>(count);
          for (int i = 0; i < count; i++) {
            childNodes.add(pNode.getChildAt(i));
          }
          for (TreeNode childNode: childNodes) {
            if (childNode instanceof ProblemDescriptionNode) {
              createdNodesConsumer.apply(pNode);
              break;
            }
            LOG.assertTrue(childNode instanceof RefElementNode, childNode.getClass().getName());
            final RefElementNode elementNode = (RefElementNode)childNode;
            final Set<RefElementNode> parentNodes = new LinkedHashSet<RefElementNode>();
            if (pNode.getPackageName() != null) {
              parentNodes.add(elementNode);
            } else {
              boolean hasElementNodeUnder = true;
              for(int e = 0; e < elementNode.getChildCount(); e++) {
                final TreeNode grandChildNode = elementNode.getChildAt(e);
                if (grandChildNode instanceof ProblemDescriptionNode) {
                  hasElementNodeUnder = false;
                  break;
                }
                LOG.assertTrue(grandChildNode instanceof RefElementNode);
                parentNodes.add((RefElementNode)grandChildNode);
              }
              if (!hasElementNodeUnder) {
                createdNodesConsumer.apply(elementNode);
                continue;
              }
            }
            for (RefElementNode parentNode : parentNodes) {
              final List<ProblemDescriptionNode> nodes = new ArrayList<ProblemDescriptionNode>();
              TreeUtil.traverse(parentNode, new TreeUtil.Traverse() {
                @Override
                public boolean accept(final Object node) {
                  if (node instanceof ProblemDescriptionNode) {
                    nodes.add((ProblemDescriptionNode)node);
                  }
                  return true;
                }
              });
              if (nodes.isEmpty()) continue;  //FilteringInspectionTool == DeadCode
              parentNode.removeAllChildren();
              for (ProblemDescriptionNode node : nodes) {
                parentNode.add(node);
              }
            }
            for (RefElementNode node : parentNodes) {
              createdNodesConsumer.apply(node);
            }
          }
        }
      }
    }
  }

  @NotNull
  protected static RefElementNode addNodeToParent(@NotNull UserObjectContainer container,
                                                  @NotNull InspectionToolPresentation presentation,
                                                  final InspectionTreeNode parentNode) {
    final RefElementNode nodeToBeAdded = container.createNode(presentation);
    final Ref<Boolean> firstLevel = new Ref<Boolean>(true);
    RefElementNode prevNode = null;
    final Ref<RefElementNode> result = new Ref<RefElementNode>();
    while (true) {
      final RefElementNode currentNode = firstLevel.get() ? nodeToBeAdded : container.createNode(presentation);
      final UserObjectContainer finalContainer = container;
      final RefElementNode finalPrevNode = prevNode;
      TreeUtil.traverseDepth(parentNode, new TreeUtil.Traverse() {
        @Override
        public boolean accept(Object node) {
          if (node instanceof RefElementNode) {
            final RefElementNode refElementNode = (RefElementNode)node;
            final Object userObject = finalContainer.getUserObject();
            final Object object = node instanceof OfflineRefElementNode ? ((OfflineRefElementNode) refElementNode).getOfflineDescriptor() : refElementNode.getUserObject();
            if ((object == null || userObject.getClass().equals(object.getClass())) && finalContainer.areEqual(object, userObject)) {
              if (firstLevel.get()) {
                result.set(refElementNode);
                return false;
              }
              else {
                refElementNode.insertByOrder(finalPrevNode, false);
                result.set(nodeToBeAdded);
                return false;
              }
            }
          }
          return true;
        }
      });
      if(!result.isNull()) return result.get();

      if (!firstLevel.get()) {
        currentNode.insertByOrder(prevNode, false);
      }
      final UserObjectContainer owner = container.getOwner();
      if (owner == null) {
        parentNode.insertByOrder(currentNode, false);
        return nodeToBeAdded;
      }
      container = owner;
      prevNode = currentNode;
      firstLevel.set(false);
    }
  }

  @SuppressWarnings({"ConstantConditions"}) //class cast suppression
  public static InspectionTreeNode merge(InspectionTreeNode child, InspectionTreeNode parent, boolean merge) {
    if (merge) {
      for (int i = 0; i < parent.getChildCount(); i++) {
        InspectionTreeNode current = (InspectionTreeNode)parent.getChildAt(i);
        if (child.getClass() != current.getClass()) {
          continue;
        }
        if (current instanceof InspectionPackageNode) {
          if (((InspectionPackageNode)current).getPackageName().compareTo(((InspectionPackageNode)child).getPackageName()) == 0) {
            processDepth(child, current);
            return current;
          }
        }
        else if (current instanceof RefElementNode) {
          if (InspectionResultsViewComparator.getInstance().compare(current, child) == 0) {
            processDepth(child, current);
            return current;
          }
        }
        else if (current instanceof InspectionNode) {
          if (((InspectionNode)current).getToolWrapper().getShortName().compareTo(((InspectionNode)child).getToolWrapper().getShortName()) == 0) {
            processDepth(child, current);
            return current;
          }
        }
        else if (current instanceof InspectionModuleNode) {
          if (((InspectionModuleNode)current).getName().compareTo(((InspectionModuleNode)child).getName()) == 0) {
            processDepth(child, current);
            return current;
          }
        }
      }
    }
    return parent.insertByOrder(child, false);
  }

  private static void processDepth(final InspectionTreeNode child, final InspectionTreeNode current) {
    InspectionTreeNode[] children = new InspectionTreeNode[child.getChildCount()];
    for (int i = 0; i < children.length; i++) {
      children[i] = (InspectionTreeNode)child.getChildAt(i);
    }
    for (InspectionTreeNode node : children) {
      merge(node, current, true);
    }
  }
}
