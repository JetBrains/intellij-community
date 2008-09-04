/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

public class AbstractTreeBuilder implements Disposable {
  private AbstractTreeUi myUi;

  public AbstractTreeBuilder(JTree tree,
                             DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure,
                             Comparator<NodeDescriptor> comparator) {
    init(tree, treeModel, treeStructure, comparator, true);
  }
  public AbstractTreeBuilder(JTree tree,
                             DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure,
                             Comparator<NodeDescriptor> comparator,
                             boolean updateIfInactive) {
    init(tree, treeModel, treeStructure, comparator, updateIfInactive);
  }

  protected AbstractTreeBuilder() {
  }


  protected void init(final JTree tree, final DefaultTreeModel treeModel, final AbstractTreeStructure treeStructure, final Comparator<NodeDescriptor> comparator) {

    myUi = createUi();
    getUi().init(this, tree, treeModel, treeStructure, comparator);
  }
  protected void init(final JTree tree, final DefaultTreeModel treeModel, final AbstractTreeStructure treeStructure, final Comparator<NodeDescriptor> comparator,
                      final boolean updateIfInactive) {

    myUi = createUi();
    getUi().init(this, tree, treeModel, treeStructure, comparator, updateIfInactive);
  }

  AbstractTreeUi createUi() {
    return new AbstractTreeUi();
  }

  public final void select(final Object element, @Nullable final Runnable onDone) {
    getUi().select(element, onDone);
  }

  public final void select(final Object element, @Nullable final Runnable onDone, boolean addToSelection) {
    getUi().select(element, onDone, addToSelection);
  }

  public final void expand(Object element, @Nullable Runnable onDone) {
    getUi().expand(element, onDone);
  }

  public final void select(final Object[] elements, @Nullable final Runnable onDone) {
    getUi().select(elements, onDone);
  }

  public final void select(final Object[] elements, @Nullable final Runnable onDone, boolean addToSelection) {
    getUi().select(elements, onDone, addToSelection);
  }

  protected AbstractTreeNode createSearchingTreeNodeWrapper() {
    return new AbstractTreeNodeWrapper();
  }

  public final AbstractTreeBuilder setClearOnHideDelay(final long clearOnHideDelay) {
    getUi().setClearOnHideDelay(clearOnHideDelay);
    return this;
  }

  protected AbstractTreeUpdater createUpdater() {
    return new AbstractTreeUpdater(this);
  }

  protected final AbstractTreeUpdater getUpdater() {
    return getUi().getUpdater();
  }

  public final boolean addSubtreeToUpdateByElement(Object element) {
    return getUpdater().addSubtreeToUpdateByElement(element);
  }

  public final void addSubtreeToUpdate(DefaultMutableTreeNode node) {
    getUi().addSubtreeToUpdate(node);
  }

  public final void addSubtreeToUpdate(DefaultMutableTreeNode node, Runnable afterUpdate) {
    getUi().addSubtreeToUpdate(node, afterUpdate);
  }

  public final DefaultMutableTreeNode getRootNode() {
    return getUi().getRootNode();
  }

  public final void setNodeDescriptorComparator(Comparator<NodeDescriptor> nodeDescriptorComparator) {
    getUi().setNodeDescriptorComparator(nodeDescriptorComparator);
  }

  /**
  * node descriptor getElement contract is as follows:
  * 1.TreeStructure always returns & recieves "treestructure" element returned by getTreeStructureElement
  * 2.Paths contain "model" element returned by getElement
  */

  protected Object getTreeStructureElement(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getElement();
  }


  protected void updateNode(final DefaultMutableTreeNode node) {
    getUi().doUpdateNode(node);
  }

  protected boolean validateNode(final Object child) {
    return true;
  }

  protected boolean isDisposeOnCollapsing(NodeDescriptor nodeDescriptor) {
    return true;
  }

  public final JTree getTree() {
    return getUi().getTree();
  }

  public final AbstractTreeStructure getTreeStructure() {
    return getUi().getTreeStructure();
  }

  public final void setTreeStructure(final AbstractTreeStructure structure) {
    getUi().setTreeStructure(structure);
  }

  public void updateFromRoot() {
    getUi().doUpdateFromRoot();
  }

  public void initRootNode() {
    getUi().initRootNode();
  }

  /**
   * @deprecated
   * @param element
   */
  public void buildNodeForElement(Object element) {
    getUi().buildNodeForElement(element);
  }

  /**
   * @deprecated
   * @param element
   * @return
   */
  @Nullable
  public DefaultMutableTreeNode getNodeForElement(Object element) {
    return getUi().getNodeForElement(element, false);
  }

  public void cleanUp() {
    getUi().doCleanUp();
  }

  @Nullable
  protected ProgressIndicator createProgressIndicator() {
    return null;
  }

  protected void expandNodeChildren(final DefaultMutableTreeNode node) {
    getUi().doExpandNodeChildren(node);
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    return getTreeStructure().getRootElement() == nodeDescriptor.getElement();
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor descriptor) {
    return false;
  }



  protected boolean isSmartExpand() {
    return true;
  }

  public final boolean isDisposed() {
    return getUi() == null || getUi().isReleased();
  }

  /**
   * @deprecated
   * @param node
   */
  public final void updateSubtree(final DefaultMutableTreeNode node) {
    getUi().updateSubtree(node);
  }

  public final boolean wasRootNodeInitialized() {
    return getUi().wasRootNodeInitialized();
  }

  public final boolean isNodeBeingBuilt(final TreePath path) {
    return getUi().isNodeBeingBuilt(path);
  }

  /**
   * @deprecated
   * @param path
   */
  public final void buildNodeForPath(final Object[] path) {
    getUi().buildNodeForPath(path);
  }

  /**
   * @deprecated
   */
  public final DefaultMutableTreeNode getNodeForPath(final Object[] path) {
    return getUi().getNodeForPath(path);
  }

  protected Object findNodeByElement(final Object element) {
    return getUi().findNodeByElement(element);
  }

  public static boolean isLoadingNode(final DefaultMutableTreeNode node) {
    return AbstractTreeUi.isLoadingNode(node);
  }

  public static class AbstractTreeNodeWrapper extends AbstractTreeNode<Object> {
    public AbstractTreeNodeWrapper() {
      super(null, null);
    }

    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      return Collections.emptyList();
    }

    public void update(PresentationData presentation) {
    }
  }

  public final AbstractTreeUi getUi() {
    return myUi;
  }

  public void dispose() {
    if (isDisposed()) return;

    myUi.release();
    myUi = null;
  }

  protected boolean updateNodeDescriptor(final NodeDescriptor descriptor) {
    return getUi().doUpdateNodeDescriptor(descriptor);
  }

  public final DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)getTree().getModel();
  }

  @NotNull
  public final Set<Object> getSelectedElements() {
    return getUi().getSelectedElements();
  }

  @NotNull
  public final <T> Set<T> getSelectedElements(Class<T> elementClass) {
    Set<T> result = new HashSet<T>();
    for (Object o : getSelectedElements()) {
      if (elementClass.isInstance(o)) {
        //noinspection unchecked
        result.add((T) o);
      }
    }
    return result;
  }
}
