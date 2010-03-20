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

import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

public abstract class InspectionRVContentProvider {
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("#" + InspectionRVContentProvider.class.getName());

  public InspectionRVContentProvider(final Project project) {
    myProject = project;
  }

  protected interface UserObjectContainer<T> {
    @Nullable
    UserObjectContainer<T> getOwner();

    RefElementNode createNode(InspectionTool tool);

    T getUserObject();

    @Nullable
    String getModule();

    boolean areEqual(final T o1, final T o2);

    boolean supportStructure();
  }

  public abstract boolean checkReportedProblems(final InspectionTool tool);

  @Nullable
  public abstract QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree);


  public abstract void appendToolNodeContent(final InspectionNode toolNode,
                                             final InspectionTreeNode parentNode,
                                             final boolean showStructure);

  protected abstract void appendDescriptor(final InspectionTool tool,
                                           final UserObjectContainer container,
                                           final InspectionPackageNode pNode,
                                           final boolean canPackageRepeat);

  public boolean isContentLoaded() {
    return true;
  }

  protected <T> List<InspectionTreeNode> buildTree(final Map<String, Set<T>> packageContents,
                                                   final boolean canPackageRepeat,
                                                   final InspectionTool tool,
                                                   final Function<T, UserObjectContainer<T>> computeContainer,
                                                   final boolean showStructure) {
    final List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    final Map<String, Map<String, InspectionPackageNode>> module2PackageMap = new HashMap<String, Map<String, InspectionPackageNode>>();
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
          pNode = new InspectionPackageNode(packageName);
          packageNodes.put(packageName, pNode);
        }
        appendDescriptor(tool, container, pNode, canPackageRepeat);
      }
    }
    if (supportStructure) {
      final HashMap<String, InspectionModuleNode> moduleNodes = new HashMap<String, InspectionModuleNode>();
      for (final String moduleName : module2PackageMap.keySet()) {
        final Map<String, InspectionPackageNode> packageNodes = module2PackageMap.get(moduleName);
        for (InspectionPackageNode packageNode : packageNodes.values()) {
          if (packageNode.getChildCount() > 0) {
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
              } else {
                content.addAll(packageNodes.values());
                break;
              }
            }
            if (packageNode.getPackageName() != null) {
              moduleNode.add(packageNode);
            } else {
              for(int i = packageNode.getChildCount() - 1; i >= 0; i--) {
                moduleNode.add((MutableTreeNode)packageNode.getChildAt(i));
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
          for (int i = 0; i < pNode.getChildCount(); i++) {
            final TreeNode childNode = pNode.getChildAt(i);
            if (childNode instanceof ProblemDescriptionNode) {
              content.add(pNode);
              break;
            }
            LOG.assertTrue(childNode instanceof RefElementNode, childNode.getClass().getName());
            final RefElementNode elementNode = (RefElementNode)childNode;
            final Set<RefElementNode> parentNodes = new HashSet<RefElementNode>();
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
                content.add(elementNode);
                continue;
              }
            }
            for (RefElementNode parentNode : parentNodes) {
              final List<ProblemDescriptionNode> nodes = new ArrayList<ProblemDescriptionNode>();
              TreeUtil.traverse(parentNode, new TreeUtil.Traverse() {
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
            content.addAll(parentNodes);
          }
        }
      }
    }
    return content;
  }

  protected static RefElementNode addNodeToParent(UserObjectContainer container,
                                                  final InspectionTool tool,
                                                  final InspectionTreeNode parentNode) {
    final Set<InspectionTreeNode> children = new THashSet<InspectionTreeNode>(1000);
    TreeUtil.traverseDepth(parentNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        children.add((InspectionTreeNode)node);
        return true;
      }
    });
    final RefElementNode nodeToBeAdded = container.createNode(tool);
    boolean firstLevel = true;
    RefElementNode prevNode = null;
    while (true) {
      final RefElementNode currentNode = firstLevel ? nodeToBeAdded : container.createNode(tool);
      for (InspectionTreeNode node : children) {
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          if (container.areEqual(refElementNode.getUserObject(), container.getUserObject())) {
            if (firstLevel) {
              return refElementNode;
            }
            else {
              refElementNode.add(prevNode);
              return nodeToBeAdded;
            }
          }
        }
      }
      if (!firstLevel) {
        currentNode.add(prevNode);
      }
      final UserObjectContainer owner = container.getOwner();
      if (owner == null) {
        parentNode.add(currentNode);
        return nodeToBeAdded;
      }
      container = owner;
      prevNode = currentNode;
      firstLevel = false;
    }
  }

  @SuppressWarnings({"ConstantConditions"}) //class cast suppression
  protected static void merge(InspectionTreeNode child, InspectionTreeNode parent, boolean merge) {
    if (merge) {
      for (int i = 0; i < parent.getChildCount(); i++) {
        InspectionTreeNode current = (InspectionTreeNode)parent.getChildAt(i);
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
          if (((RefElementNode)current).getElement().getName().compareTo(((RefElementNode)child).getElement().getName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
        else if (current instanceof InspectionNode) {
          if (((InspectionNode)current).getTool().getShortName().compareTo(((InspectionNode)child).getTool().getShortName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
        else if (current instanceof InspectionModuleNode) {
          if (((InspectionModuleNode)current).getName().compareTo(((InspectionModuleNode)child).getName()) == 0) {
            processDepth(child, current);
            return;
          }
        }
        else if (current instanceof ProblemDescriptionNode) {
          if (((ProblemDescriptionNode)current).getDescriptor().getDescriptionTemplate()
            .compareTo(((ProblemDescriptionNode)child).getDescriptor().getDescriptionTemplate()) == 0) {
            processDepth(child, current);
            return;
          }
        }
      }
    }
    parent.add(child);
  }

  private static void processDepth(final InspectionTreeNode child, final InspectionTreeNode current) {
    for (int j = 0; j < child.getChildCount(); j++) {
      merge((InspectionTreeNode)child.getChildAt(j), current, true);
    }
  }
}
