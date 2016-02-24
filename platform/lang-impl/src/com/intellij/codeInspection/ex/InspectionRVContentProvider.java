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
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.ui.tree.*;
import com.intellij.codeInspection.ui.tree.InspectionTreeNode;
import com.intellij.codeInspection.ui.tree.ProblemDescriptionNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.*;
import java.util.function.Predicate;

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

  public Collection<ScopeToolState> getTools(Tools tools) {
    return tools.getTools();
  }

  public boolean hasQuickFixes(InspectionTreeBuilder tree) {
    for (CommonProblemDescriptor descriptor : tree.getSelectedDescriptors()) {
      QuickFix[] fixes = descriptor.getFixes();
      if (fixes != null && fixes.length != 0) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public abstract QuickFixAction[] getQuickFixes(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionTreeBuilder tree);


  public void appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                    @NotNull InspectionNode toolNode,
                                    @NotNull InspectionTreeNode parentNode,
                                    final boolean showStructure) {
    InspectionToolWrapper wrapper = toolNode.getToolWrapper();
    InspectionToolPresentation presentation = context.getPresentation(wrapper);
    Map<String, Set<RefEntity>> content = presentation.getContent();
    Map<RefEntity, CommonProblemDescriptor[]> problems = presentation.getProblemElements();
    appendToolNodeContent(context, toolNode, parentNode, showStructure, content, problems, null);
  }

  public abstract void appendToolNodeContent(@NotNull GlobalInspectionContextImpl context,
                                             @NotNull InspectionNode toolNode,
                                             @NotNull InspectionTreeNode parentNode,
                                             final boolean showStructure,
                                             @NotNull Map<String, Set<RefEntity>> contents,
                                             @NotNull Map<RefEntity, CommonProblemDescriptor[]> problems,
                                             @Nullable final DefaultTreeModel model);

  protected abstract void appendDescriptor(@NotNull GlobalInspectionContextImpl context,
                                           @NotNull InspectionToolWrapper toolWrapper,
                                           @NotNull UserObjectContainer container,
                                           @NotNull InspectionPackageNode pNode,
                                           final boolean canPackageRepeat);

  public boolean isContentLoaded() {
    return true;
  }

  protected <T> List<InspectionTreeNode> buildTree(@NotNull GlobalInspectionContextImpl context,
                                                   @NotNull Map<String, Set<T>> packageContents,
                                                   final boolean canPackageRepeat,
                                                   @NotNull InspectionToolWrapper toolWrapper,
                                                   @NotNull Function<T, UserObjectContainer<T>> computeContainer,
                                                   final boolean showStructure) {
    final List<InspectionTreeNode> content = new ArrayList<>();
    final Map<String, Map<String, InspectionPackageNode>> module2PackageMap = new HashMap<>();
    boolean supportStructure = showStructure;
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
          pNode = new InspectionPackageNode(myProject, packageName);
          packageNodes.put(packageName, pNode);
        }
        appendDescriptor(context, toolWrapper, container, pNode, canPackageRepeat);
      }
    }
    if (supportStructure) {
      final HashMap<String, InspectionModuleNode> moduleNodes = new HashMap<String, InspectionModuleNode>();
      for (final String moduleName : module2PackageMap.keySet()) {
        final Map<String, InspectionPackageNode> packageNodes = module2PackageMap.get(moduleName);
        for (InspectionPackageNode packageNode : packageNodes.values()) {
          if (packageNode.getChildren().size() > 0) {
            InspectionModuleNode moduleNode = moduleNodes.get(moduleName);
            if (moduleNode == null) {
              if (moduleName != null) {
                final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleName);
                if (module != null) {
                  moduleNode = new InspectionModuleNode(module);
                  moduleNodes.put(moduleName, moduleNode);
                }
                else { //module content was removed ?
                  continue;
                }
              }
              else {
                content.addAll(packageNodes.values());
                break;
              }
            }
            if (packageNode.getPackageName() != null) {
              moduleNode.add(packageNode);
            }
            else {
              for (InspectionTreeNode node : packageNode.getChildren()) {
                moduleNode.add(node);
              }
            }
          }
        }
      }
      content.addAll(moduleNodes.values());
    }
    else {
      for (Map<String, InspectionPackageNode> packageNodes : module2PackageMap.values()) {
        for (InspectionPackageNode pNode : packageNodes.values()) {
          for (InspectionTreeNode childNode : pNode.getChildren()) {
            if (childNode instanceof ProblemDescriptionNode) {
              content.add(pNode);
              break;
            }
            LOG.assertTrue(childNode instanceof RefElementNode, childNode.getClass().getName());
            final RefElementNode elementNode = (RefElementNode)childNode;
            final Set<RefElementNode> parentNodes = new LinkedHashSet<>();
            if (pNode.getPackageName() != null) {
              parentNodes.add(elementNode);
            }
            else {
              boolean hasElementNodeUnder = true;
              for (Object grandChildNode : elementNode.getChildren()) {
                if (grandChildNode instanceof ProblemDescriptionNode) {
                  hasElementNodeUnder = false;
                  break;
                }
                LOG.assertTrue(grandChildNode instanceof RefElementNode);
                parentNodes.add((RefElementNode)grandChildNode);
              }

              if (!hasElementNodeUnder) {
                content.add(elementNode);
                continue;
              }
            }
            for (RefElementNode parentNode : parentNodes) {
              final List<ProblemDescriptionNode> nodes = new ArrayList<>();
              traverseDepth(parentNode, n -> {
                if (n instanceof ProblemDescriptionNode) {
                  nodes.add((ProblemDescriptionNode)n);
                }
                return true;
              });

              if (nodes.isEmpty()) continue;  //FilteringInspectionTool == DeadCode
              parentNode.removeAllChildren();
              for (ProblemDescriptionNode node : nodes) {
                parentNode.add(node);
              }
            }
            content.addAll(parentNodes);
          }
        }
      }
    }
    return content;
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
      traverseDepth(parentNode, new Predicate<InspectionTreeNode>() {
        @Override
        public boolean test(InspectionTreeNode node) {
          if (node instanceof RefElementNode) {
            final RefElementNode refElementNode = (RefElementNode)node;
            final Object userObject = finalContainer.getUserObject();
            final Object object = refElementNode.getValue();
            if ((object == null || userObject.getClass().equals(object.getClass())) && finalContainer.areEqual(object, userObject)) {
              if (firstLevel.get()) {
                result.set(refElementNode);
                return false;
              }
              else {
                refElementNode.add(finalPrevNode);
                result.set(nodeToBeAdded);
                return false;
              }
            }
          }
          return true;
        }
      });
      if (!result.isNull()) return result.get();

      if (!firstLevel.get()) {
        currentNode.add(prevNode);
      }
      final UserObjectContainer owner = container.getOwner();
      if (owner == null) {
        parentNode.add(currentNode);
        return nodeToBeAdded;
      }
      container = owner;
      prevNode = currentNode;
      firstLevel.set(false);
    }
  }

  @SuppressWarnings({"ConstantConditions"}) //class cast suppression
  protected static void merge(InspectionTreeNode child, InspectionTreeNode parent, boolean merge) {
    if (merge) {
      final Collection<InspectionTreeNode> children = parent.getChildren();
      for (InspectionTreeNode current : children) {
        if (child.getClass() != current.getClass()) {
          continue;
        }
        if (current instanceof InspectionPackageNode) {
          if (((InspectionPackageNode)current).getPackageName().compareTo(((InspectionPackageNode)child).getPackageName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
        else if (current instanceof RefElementNode) {
          if (((RefElementNode)current).getRefElement().getName().compareTo(((RefElementNode)child).getRefElement().getName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
        else if (current instanceof InspectionNode) {
          if (((InspectionNode)current).getToolWrapper().getShortName()
                .compareTo(((InspectionNode)child).getToolWrapper().getShortName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
        else if (current instanceof InspectionModuleNode) {
          if (current.getName().compareTo(child.getName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
      }
    }
    add(child, parent);
  }

  protected static void add(final InspectionTreeNode child, final InspectionTreeNode parent) {
    parent.add(child);
  }

  private static void processDepth(final InspectionTreeNode child, final InspectionTreeNode current) {
    final Collection<InspectionTreeNode> childChildren = child.getChildren();
    for (InspectionTreeNode node : childChildren.toArray(new InspectionTreeNode[child.getChildren().size()])) {
      merge(node, current, true);
    }
  }

  public static boolean traverse(@NotNull final InspectionTreeNode node, @NotNull final Predicate<InspectionTreeNode> traverse) {
    Collection<InspectionTreeNode> children = node.getChildren();
    for (InspectionTreeNode o : children) {
      if (!traverse(o, traverse)) return false;
    }
    return traverse.test(node);
  }

  public static boolean traverseDepth(@NotNull final InspectionTreeNode node, @NotNull final Predicate<InspectionTreeNode> traverse) {
    if (!traverse.test(node)) return false;
    Collection<InspectionTreeNode> children = node.getChildren();
    for (InspectionTreeNode o : children) {
      if (!traverseDepth(o, traverse)) return false;
    }
    return true;
  }
}
