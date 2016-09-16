/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNestingRulesProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.NestingTreeNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * <code>NestingTreeStructureProvider</code> moves some files in the Project View to be shown as children of another peer file. Standard use
 * case is to improve folder contents presentation when it contains both source file and its compiled output. For example generated
 * <code>foo.min.js</code> file will be shown as a child of <code>foo.js</code> file.<br/>
 * Nesting logic is based on file names only. Rules about files that should be nested are provided by
 * <code>com.intellij.projectViewNestingRulesProvider</code> extensions.
 *
 * @see ProjectViewNestingRulesProvider
 */
public class NestingTreeStructureProvider implements TreeStructureProvider, DumbAware {

  private static ExtensionPointName<ProjectViewNestingRulesProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.projectViewNestingRulesProvider");

  private static final Logger LOG = Logger.getInstance(NestingTreeStructureProvider.class);
  private Set<NestingRule> myNestingRules;

  @NotNull
  private Collection<NestingRule> getNestingRules() {
    if (myNestingRules == null) {
      myNestingRules = new THashSet<>();

      final MultiMap<String, String> childToParentSuffix = new MultiMap<>();
      final MultiMap<String, String> parentToChildSuffix = new MultiMap<>();

      final ProjectViewNestingRulesProvider.Consumer consumer = new ProjectViewNestingRulesProvider.Consumer() {
        @Override
        public void addNestingRule(@NotNull final String parentFileSuffix, @NotNull final String childFileSuffix) {
          LOG.assertTrue(!parentFileSuffix.isEmpty() && !childFileSuffix.isEmpty(), "file suffix must not be empty");
          LOG.assertTrue(!parentFileSuffix.equals(childFileSuffix), "parent and child suffixes must be different: " + parentFileSuffix);

          myNestingRules.add(new NestingRule(parentFileSuffix, childFileSuffix));
          childToParentSuffix.putValue(childFileSuffix, parentFileSuffix);
          parentToChildSuffix.putValue(parentFileSuffix, childFileSuffix);

          // for all cases like A -> B -> C we also add a rule A -> C
          for (String s : parentToChildSuffix.get(childFileSuffix)) {
            myNestingRules.add(new NestingRule(parentFileSuffix, s));
            parentToChildSuffix.putValue(parentFileSuffix, s);
            childToParentSuffix.putValue(s, parentFileSuffix);
          }

          for (String s : childToParentSuffix.get(parentFileSuffix)) {
            myNestingRules.add(new NestingRule(s, childFileSuffix));
            parentToChildSuffix.putValue(s, childFileSuffix);
            childToParentSuffix.putValue(childFileSuffix, s);
          }
        }
      };

      for (ProjectViewNestingRulesProvider provider : EP_NAME.getExtensions()) {
        provider.addFileNestingRules(consumer);
      }
    }

    return myNestingRules;
  }

  @NotNull
  private static Collection<NestingRule> getNestingRulesStatic(@NotNull final Project project) {
    for (TreeStructureProvider provider : Extensions.getExtensions(TreeStructureProvider.EP_NAME, project)) {
      if (provider instanceof NestingTreeStructureProvider) {
        return ((NestingTreeStructureProvider)provider).getNestingRules();
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public Object getData(final Collection<AbstractTreeNode> selected, final String dataName) {
    return null;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull final AbstractTreeNode parent,
                                             @NotNull final Collection<AbstractTreeNode> children,
                                             final ViewSettings settings) {
    if (!(parent instanceof PsiDirectoryNode)) return children;

    final Collection<NestingRule> rules = getNestingRules();
    if (rules.isEmpty()) return children;

    final MultiMap<PsiFileNode, PsiFileNode> parentToChildren = calcParentToChildren(children, rules);
    if (parentToChildren.isEmpty()) return children;

    // initial ArrayList size may be not exact, not a big problem
    final Collection<AbstractTreeNode> newChildren = new ArrayList<>(children.size() - parentToChildren.size());

    final Set<PsiFileNode> childrenToMoveDown = new THashSet<>(parentToChildren.values());

    for (AbstractTreeNode node : children) {
      if (!(node instanceof PsiFileNode)) {
        newChildren.add(node);
        continue;
      }

      if (childrenToMoveDown.contains(node)) {
        continue;
      }

      final Collection<PsiFileNode> childrenOfThisFile = parentToChildren.get((PsiFileNode)node);
      if (childrenOfThisFile.isEmpty()) {
        newChildren.add(node);
        continue;
      }

      newChildren.add(new NestingTreeNode((PsiFileNode)node, childrenOfThisFile));
    }

    return newChildren;
  }

  // Algorithm is similar to calcParentToChildren(), but a bit simpler, because we have one specific parentFile.
  public static Collection<ChildFileInfo> getFilesShownAsChildrenInProjectView(@NotNull final Project project,
                                                                               @NotNull final VirtualFile parentFile) {
    LOG.assertTrue(!parentFile.isDirectory());

    final VirtualFile dir = parentFile.getParent();
    if (dir == null) return Collections.emptyList();

    final Collection<NestingRule> rules = getNestingRulesStatic(project);
    if (rules.isEmpty()) return Collections.emptyList();

    final VirtualFile[] children = dir.getChildren();
    if (children.length <= 1) return Collections.emptyList();

    final Collection<NestingRule> rulesWhereItCanBeParent = filterRules(rules, parentFile.getName(), true);
    if (rulesWhereItCanBeParent.isEmpty()) return Collections.emptyList();

    final Collection<NestingRule> rulesWhereItCanBeChild = filterRules(rules, parentFile.getName(), false);

    final SmartList<ChildFileInfo> result = new SmartList<>();

    for (VirtualFile child : children) {
      if (child.isDirectory()) continue;
      if (child.equals(parentFile)) continue;

      // if given parentFile itself appears to be a child of some other file, it means that it is not shown as parent node in Project View
      for (NestingRule rule : rulesWhereItCanBeChild) {
        final String childName = child.getName();

        final Couple<Boolean> c = checkMatchingAsParentOrChild(rule, childName);
        final boolean matchesParent = c.first;

        if (matchesParent) {
          final String baseName = childName.substring(0, childName.length() - rule.myParentFileSuffix.length());
          if (parentFile.getName().equals(baseName + rule.myChildFileSuffix)) {
            return Collections.emptyList(); // parentFile itself appears to be a child of childFile
          }
        }
      }

      for (NestingRule rule : rulesWhereItCanBeParent) {
        final String childName = child.getName();

        final Couple<Boolean> c = checkMatchingAsParentOrChild(rule, childName);
        final boolean matchesChild = c.second;

        if (matchesChild) {
          final String baseName = childName.substring(0, childName.length() - rule.myChildFileSuffix.length());
          if (parentFile.getName().equals(baseName + rule.myParentFileSuffix)) {
            result.add(new ChildFileInfo(child, baseName));
          }
        }
      }
    }

    return result;
  }

  /**
   * @return only those rules where given <code>fileName</code> can potentially be a parent (if <code>parentNotChild</code> is <code>true</code>)
   * or only those rules where given <code>fileName</code> can potentially be a child (if <code>parentNotChild</code> is <code>false</code>)
   */
  @NotNull
  private static Collection<NestingRule> filterRules(@NotNull final Collection<NestingRule> rules,
                                                     @NotNull final String fileName,
                                                     final boolean parentNotChild) {
    final SmartList<NestingRule> result = new SmartList<>();
    for (NestingRule rule : rules) {
      final Couple<Boolean> c = checkMatchingAsParentOrChild(rule, fileName);
      final boolean matchesParent = c.first;
      final boolean matchesChild = c.second;

      if (!matchesChild && !matchesParent) continue;

      if (matchesParent && parentNotChild) {
        result.add(rule);
      }

      if (matchesChild && !parentNotChild) {
        result.add(rule);
      }
    }

    return result;
  }

  /*
    This is a graph theory problem. PsiFileNodes are graph nodes.
    Edges go from parent file to child file according to NestingRules, for example foo.js->foo.min.js.
    Parent may have several children. Child may have several parents.
    There may be cycles with 3 or more nodes, but cycle with 2 nodes (A->B and B->A) is impossible because parentFileSuffix != childFileSuffix
    For each child its outbound edges are removed. For example in case of a cycle all edges that form it are removed. In case of A->B->C only A->B remains.
    As a result we get a number of separated parent-to-many-children sub-graphs, and use them to nest child files under parent file in Project View.
    One child still may have more than one parent. For real use cases it is not expected to happen, but anyway it's not a big problem, it will be shown as a subnode more than once.
   */
  @NotNull
  private static MultiMap<PsiFileNode, PsiFileNode> calcParentToChildren(@NotNull final Collection<AbstractTreeNode> nodes,
                                                                         @NotNull final Collection<NestingRule> rules) {
    // result that will contain number of separated parent-to-many-children sub-graphs
    MultiMap<PsiFileNode, PsiFileNode> parentToChildren = null;

    Set<PsiFileNode> allChildNodes = null; // helps to remove all outbound edges of a node that has inbound edge itself
    Map<Pair<String, NestingRule>, Edge<PsiFileNode>> baseNameAndRuleToEdge = null; // temporary map for building edges

    for (AbstractTreeNode node : nodes) {
      if (!(node instanceof PsiFileNode)) continue;

      final PsiFile file = ((PsiFileNode)node).getValue();
      if (file == null) continue;

      for (NestingRule rule : rules) {
        final String fileName = file.getName();

        final Couple<Boolean> c = checkMatchingAsParentOrChild(rule, fileName);
        final boolean matchesParent = c.first;
        final boolean matchesChild = c.second;

        if (!matchesChild && !matchesParent) continue;

        if (baseNameAndRuleToEdge == null) {
          baseNameAndRuleToEdge = new THashMap<>();
          parentToChildren = new MultiMap<>();
          allChildNodes = new THashSet<>();
        }

        if (matchesParent) {
          final String baseName = fileName.substring(0, fileName.length() - rule.myParentFileSuffix.length());
          final Edge<PsiFileNode> edge = getOrCreateEdge(baseNameAndRuleToEdge, baseName, rule);
          edge.from = (PsiFileNode)node;
          updateInfoIfEdgeComplete(parentToChildren, allChildNodes, edge);
        }

        if (matchesChild) {
          final String baseName = fileName.substring(0, fileName.length() - rule.myChildFileSuffix.length());
          final Edge<PsiFileNode> edge = getOrCreateEdge(baseNameAndRuleToEdge, baseName, rule);
          edge.to = (PsiFileNode)node;
          updateInfoIfEdgeComplete(parentToChildren, allChildNodes, edge);
        }
      }
    }

    return parentToChildren == null ? MultiMap.empty() : parentToChildren;
  }

  private static Couple<Boolean> checkMatchingAsParentOrChild(@NotNull final NestingRule rule, @NotNull final String fileName) {
    boolean matchesParent = !fileName.equals(rule.myParentFileSuffix) && fileName.endsWith(rule.myParentFileSuffix);
    boolean matchesChild = !fileName.equals(rule.myChildFileSuffix) && fileName.endsWith(rule.myChildFileSuffix);

    if (matchesParent && matchesChild) {
      if (rule.myParentFileSuffix.length() > rule.myChildFileSuffix.length()) {
        matchesChild = false;
      }
      else {
        matchesParent = false;
      }
    }

    return Couple.of(matchesParent, matchesChild);
  }

  @NotNull
  private static Edge<PsiFileNode> getOrCreateEdge(@NotNull final Map<Pair<String, NestingRule>, Edge<PsiFileNode>> baseNameAndRuleToEdge,
                                                   @NotNull final String baseName,
                                                   @NotNull final NestingRule rule) {
    final Pair<String, NestingRule> baseNameAndRule = Pair.create(baseName, rule);

    Edge<PsiFileNode> edge = baseNameAndRuleToEdge.get(baseNameAndRule);
    if (edge == null) {
      edge = new Edge<>();
      baseNameAndRuleToEdge.put(baseNameAndRule, edge);
    }
    return edge;
  }

  private static void updateInfoIfEdgeComplete(@NotNull final MultiMap<PsiFileNode, PsiFileNode> parentToChildren,
                                               @NotNull final Set<PsiFileNode> allChildNodes,
                                               @NotNull final Edge<PsiFileNode> edge) {
    if (edge.from != null && edge.to != null) { // if edge complete
      allChildNodes.add(edge.to);
      parentToChildren.remove(edge.to); // nodes that appear as a child shouldn't be a parent of another edge, corresponding edges removed
      if (!allChildNodes.contains(edge.from)) {
        parentToChildren.putValue(edge.from, edge.to);
      }
    }
  }

  private static class NestingRule {
    @NotNull private final String myParentFileSuffix;
    @NotNull private final String myChildFileSuffix;

    public NestingRule(@NotNull String parentFileSuffix, @NotNull String childFileSuffix) {
      myParentFileSuffix = parentFileSuffix;
      myChildFileSuffix = childFileSuffix;
    }

    @Override
    public String toString() {
      return myParentFileSuffix + "->" + myChildFileSuffix;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof NestingRule &&
             myParentFileSuffix.equals(((NestingRule)o).myParentFileSuffix) &&
             myChildFileSuffix.equals(((NestingRule)o).myChildFileSuffix);
    }

    @Override
    public int hashCode() {
      return myParentFileSuffix.hashCode() + 239 * myChildFileSuffix.hashCode();
    }
  }

  private static class Edge<T> {
    @Nullable private T from;
    @Nullable private T to;
  }

  public static class ChildFileInfo {
    @NotNull public final VirtualFile file;
    @NotNull public final String namePartCommonWithParentFile;

    public ChildFileInfo(@NotNull final VirtualFile file, @NotNull final String namePartCommonWithParentFile) {
      this.file = file;
      this.namePartCommonWithParentFile = namePartCommonWithParentFile;
    }
  }
}
