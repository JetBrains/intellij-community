// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.NestingTreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;

import java.util.Collection;

/**
 * A file node in the Project View, which may be a parent node for other file nodes, thanks to the
 * <a href="https://www.jetbrains.com/help/idea/file-nesting-dialog.html">file nesting</a> feature.
 * <p>
 * This class is used in the following way:
 * <ul>
 *   <li>{@link NestingTreeStructureProvider} may replace {@link PsiFileNode} with {@link NestingTreeNode} if the corresponding file must
 *   show up as a parent for some other files according to the configured nesting rules.
 *   {@link NestingTreeNode} implements {@link FileNodeWithNestedFileNodes}</li>
 *   <li>Other {@link TreeStructureProvider} implementations may want to replace some file nodes with technology-specific nodes
 *   in order to have a special presentation and optionally to show the file structure ('Show members' feature of the Project View).
 *   Examples:
 *     <ul>
 *       <li>{@link com.intellij.ide.projectView.impl.ClassesTreeStructureProvider} handles Java, Groovy, Kotlin files</li>
 *       <li>{@link org.jetbrains.kotlin.idea.projectView.KotlinExpandNodeProjectViewProvider} handles Kotlin files once again</li>
 *       <li>{@link com.goide.tree.GoTreeStructureProvider} for Go</li>
 *       <li>{@link org.jetbrains.plugins.scala.projectView.ScalaTreeStructureProvider} for Scala</li>
 *     </ul>
 *   </li>
 *   <li>Not to lose the nested files, such {@link TreeStructureProvider} implementations check if the node,
 *       which they are going to replace, is an instance of {@link FileNodeWithNestedFileNodes}.
 *       If so, they make sure that the children of the replacement node include {@link #getNestedFileNodes()} of the original node</li>
 *   <li>For the replacement node, it makes sense to implement {@link FileNodeWithNestedFileNodes} as well,
 *   in case some other {@link TreeStructureProvider} decides to replace it once again</li>
 * </ul>
 */
public interface FileNodeWithNestedFileNodes {
  Collection<? extends AbstractTreeNode<?>> getNestedFileNodes();
}
