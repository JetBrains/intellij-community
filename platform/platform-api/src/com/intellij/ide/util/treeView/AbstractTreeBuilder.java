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

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Condition;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.lang.ref.WeakReference;
import java.util.*;

public class AbstractTreeBuilder implements Disposable {
  private AbstractTreeUi myUi;
  @NonNls private static final String TREE_BUILDER = "TreeBuilder";
  public static final boolean DEFAULT_UPDATE_INACTIVE = true;
  private final TransferToEDTQueue<Runnable>
    myLaterInvocator = new TransferToEDTQueue<>("Tree later invocator", runnable -> {
    runnable.run();
    return true;
  }, o -> isDisposed(), 200);


  public AbstractTreeBuilder(@NotNull JTree tree,
                             @NotNull DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure,
                             @Nullable Comparator<NodeDescriptor> comparator) {
    this(tree, treeModel, treeStructure, comparator, DEFAULT_UPDATE_INACTIVE);
  }

  public AbstractTreeBuilder(@NotNull JTree tree,
                             @NotNull DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure,
                             @Nullable Comparator<NodeDescriptor> comparator,
                             boolean updateIfInactive) {
    init(tree, treeModel, treeStructure, comparator, updateIfInactive);
  }

  protected AbstractTreeBuilder() {

  }

  protected void init(@NotNull JTree tree,
                      @NotNull DefaultTreeModel treeModel,
                      AbstractTreeStructure treeStructure,
                      @Nullable final Comparator<NodeDescriptor> comparator,
                      final boolean updateIfInactive) {

    tree.putClientProperty(TREE_BUILDER, new WeakReference<>(this));

    myUi = createUi();
    getUi().init(this, tree, treeModel, treeStructure, comparator, updateIfInactive);

    setPassthroughMode(isUnitTestingMode());
  }

  @NotNull
  protected AbstractTreeUi createUi() {
    return new AbstractTreeUi();
  }

  public final void select(final Object element) {
    if (isDisposed()) return;

    getUi().userSelect(new Object[]{element}, null, false, true);
  }

  public final void select(final Object element, @Nullable final Runnable onDone) {
    if (isDisposed()) return;

    getUi().userSelect(new Object[]{element}, new UserRunnable(onDone), false, true);
  }

  public final void select(final Object element, @Nullable final Runnable onDone, boolean addToSelection) {
    if (isDisposed()) return;

    getUi().userSelect(new Object[]{element}, new UserRunnable(onDone), addToSelection, true);
  }

  public final void select(final Object[] elements, @Nullable final Runnable onDone) {
    if (isDisposed()) return;

    getUi().userSelect(elements, new UserRunnable(onDone), false, true);
  }

  public final void select(final Object[] elements, @Nullable final Runnable onDone, boolean addToSelection) {
    if (isDisposed()) return;

    getUi().userSelect(elements, new UserRunnable(onDone), addToSelection, true);
  }

  public final void expand(Object element, @Nullable Runnable onDone) {
    if (isDisposed()) return;

    getUi().expand(element, new UserRunnable(onDone));
  }

  public final void expand(Object[] element, @Nullable Runnable onDone) {
    if (isDisposed()) return;

    getUi().expand(element, new UserRunnable(onDone));
  }

  public final void collapseChildren(Object element, @Nullable Runnable onDone) {
    if (isDisposed()) return;

    getUi().collapseChildren(element, new UserRunnable(onDone));
  }


  @NotNull
  protected AbstractTreeNode createSearchingTreeNodeWrapper() {
    return new AbstractTreeNodeWrapper();
  }

  @NotNull
  public final AbstractTreeBuilder setClearOnHideDelay(final long clearOnHideDelay) {
    if (isDisposed()) return this;

    getUi().setClearOnHideDelay(clearOnHideDelay);

    return this;
  }

  @Nullable
  protected AbstractTreeUpdater createUpdater() {
    if (isDisposed()) return null;

    AbstractTreeUpdater updater = new AbstractTreeUpdater(this);
    updater.setModalityStateComponent(MergingUpdateQueue.ANY_COMPONENT);
    return updater;
  }

  @Nullable
  protected final AbstractTreeUpdater getUpdater() {
    if (isDisposed()) return null;

    return getUi().getUpdater();
  }

  public final boolean addSubtreeToUpdateByElement(Object element) {
    if (isDisposed()) return false;

    AbstractTreeUpdater updater = getUpdater();
    return updater != null && updater.addSubtreeToUpdateByElement(element);
  }

  public final void addSubtreeToUpdate(DefaultMutableTreeNode node) {
    if (isDisposed()) return;

    getUi().addSubtreeToUpdate(node);
  }

  public final void addSubtreeToUpdate(DefaultMutableTreeNode node, Runnable afterUpdate) {
    if (isDisposed()) return;

    getUi().addSubtreeToUpdate(node, afterUpdate);
  }

  @Nullable
  public final DefaultMutableTreeNode getRootNode() {
    if (isDisposed()) return null;

    return getUi().getRootNode();
  }

  public final void setNodeDescriptorComparator(Comparator<NodeDescriptor> nodeDescriptorComparator) {
    if (isDisposed()) return;

    getUi().setNodeDescriptorComparator(nodeDescriptorComparator);
  }

  /**
   * node descriptor getElement contract is as follows:
   * 1.TreeStructure always returns & receives "treeStructure" element returned by getTreeStructureElement
   * 2.Paths contain "model" element returned by getElement
   */
  protected Object getTreeStructureElement(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getElement();
  }


  protected void updateNode(final DefaultMutableTreeNode node) {
    if (isDisposed()) return;

    getUi().doUpdateNode(node);
  }

  protected boolean validateNode(final Object child) {
    return !isDisposed() && getUi().getTreeStructure().isValid(child);
  }

  protected boolean isDisposeOnCollapsing(NodeDescriptor nodeDescriptor) {
    return true;
  }

  public final JTree getTree() {
    if (isDisposed()) return null;

    return getUi().getTree();
  }

  public final AbstractTreeStructure getTreeStructure() {
    if (isDisposed()) return null;

    return getUi().getTreeStructure();
  }

  public final void setTreeStructure(@NotNull AbstractTreeStructure structure) {
    if (isDisposed()) return;

    getUi().setTreeStructure(structure);
  }

  @Nullable
  public Object getRootElement() {
    AbstractTreeStructure structure = getTreeStructure();
    return structure == null ? null : structure.getRootElement();
  }

  /**
   * @see #queueUpdateFrom
   * @deprecated
   */
  public void updateFromRoot() {
    queueUpdate();
  }

  public void initRootNode() {
    if (isDisposed()) return;

    getUi().initRootNode();
  }

  /**
   * @see #queueUpdateFrom
   * @deprecated
   */
  @NotNull
  protected ActionCallback updateFromRootCB() {
    return queueUpdate();
  }

  @NotNull
  public final ActionCallback queueUpdate() {
    return queueUpdate(true);
  }

  @NotNull
  public final ActionCallback queueUpdate(boolean withStructure) {
    return queueUpdateFrom(getRootElement(), true, withStructure);
  }

  @NotNull
  public final ActionCallback queueUpdateFrom(final Object element, final boolean forceResort) {
    return queueUpdateFrom(element, forceResort, true);
  }

  @NotNull
  public ActionCallback queueUpdateFrom(final Object element, final boolean forceResort, final boolean updateStructure) {
    if (getUi() == null) return ActionCallback.REJECTED;

    final ActionCallback result = new ActionCallback();

    getUi().invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeBuilder.queueUpdateFrom") {
      @Override
      public void perform() {
        if (updateStructure && forceResort) {
          getUi().incComparatorStamp();
        }
        getUi().queueUpdate(element, updateStructure).notify(result);
      }
    });


    return result;
  }

  /**
   * @param element
   * @deprecated
   */
  public void buildNodeForElement(Object element) {
    if (isDisposed()) return;

    getUi().buildNodeForElement(element);
  }

  /**
   * @param element
   * @return
   * @deprecated
   */
  @Nullable
  public DefaultMutableTreeNode getNodeForElement(Object element) {
    if (isDisposed()) return null;

    return getUi().getNodeForElement(element, false);
  }

  public void cleanUp() {
    if (isDisposed()) return;

    getUi().doCleanUp();
  }

  @Nullable
  protected ProgressIndicator createProgressIndicator() {
    return null;
  }

  protected void expandNodeChildren(@NotNull DefaultMutableTreeNode node) {
    if (isDisposed()) return;

    getUi().doExpandNodeChildren(node);
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    return !isDisposed() && getRootElement() == getTreeStructureElement(nodeDescriptor);
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor descriptor) {
    return false;
  }


  protected boolean isSmartExpand() {
    return true;
  }

  public final boolean isDisposed() {
    return getUi() == null || getUi().isReleaseRequested();
  }

  /**
   * @param node
   * @deprecated
   */
  public final void updateSubtree(final DefaultMutableTreeNode node) {
    if (isDisposed()) return;

    getUi().updateSubtree(node, true);
  }

  public final boolean wasRootNodeInitialized() {
    return !isDisposed() && getUi().wasRootNodeInitialized();
  }

  public final boolean isNodeBeingBuilt(final TreePath path) {
    return !isDisposed() && getUi().isNodeBeingBuilt(path);
  }

  /**
   * @param path
   * @deprecated
   */
  public final void buildNodeForPath(final Object[] path) {
    if (isDisposed()) return;

    getUi().buildNodeForPath(path);
  }

  /**
   * @deprecated
   */
  @Nullable
  public final DefaultMutableTreeNode getNodeForPath(final Object[] path) {
    if (isDisposed()) return null;

    return getUi().getNodeForPath(path);
  }

  @Nullable
  protected Object findNodeByElement(final Object element) {
    if (isDisposed()) return null;

    return getUi().findNodeByElement(element);
  }

  public static boolean isLoadingNode(final DefaultMutableTreeNode node) {
    return AbstractTreeUi.isLoadingNode(node);
  }

  public boolean isChildrenResortingNeeded(NodeDescriptor descriptor) {
    return true;
  }

  @SuppressWarnings("SpellCheckingInspection")
  protected void runOnYeildingDone(Runnable onDone) {
    if (isDisposed()) return;

    if (myUi.isPassthroughMode() || SwingUtilities.isEventDispatchThread()) {
      onDone.run();
    }
    else {
      myLaterInvocator.offer(onDone);
    }
  }

  protected void yield(@NotNull Runnable runnable) {
    if (isDisposed()) return;

    if (myUi.isPassthroughMode()) {
      runnable.run();
    }
    else {
      myLaterInvocator.offer(runnable);
    }
  }

  public boolean isToYieldUpdateFor(DefaultMutableTreeNode node) {
    return true;
  }

  public boolean isToEnsureSelectionOnFocusGained() {
    return true;
  }

  protected void runBackgroundLoading(@NotNull final Runnable runnable) {
    if (isDisposed()) return;

    final Application app = ApplicationManager.getApplication();
    if (app != null) {
      app.runReadAction(new TreeRunnable("AbstractTreeBuilder.runBackgroundLoading") {
        @Override
        public void perform() {
          runnable.run();
        }
      });
    }
    else {
      runnable.run();
    }
  }

  protected void updateAfterLoadedInBackground(@NotNull Runnable runnable) {
    if (isDisposed()) return;

    if (myUi.isPassthroughMode()) {
      runnable.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
  }

  @NotNull
  public final ActionCallback getInitialized() {
    if (isDisposed()) {
      return ActionCallback.REJECTED;
    }
    return myUi.getInitialized();
  }

  @NotNull
  public final ActionCallback getReady(Object requestor) {
    if (isDisposed()) return ActionCallback.REJECTED;

    return myUi.getReady(requestor);
  }

  protected void sortChildren(Comparator<TreeNode> nodeComparator, DefaultMutableTreeNode node, ArrayList<TreeNode> children) {
    Collections.sort(children, nodeComparator);
  }

  public void setPassthroughMode(boolean passthrough) {
    if (isDisposed()) return;

    myUi.setPassthroughMode(passthrough);
  }

  public void expandAll(@Nullable Runnable onDone) {
    if (isDisposed()) return;

    getUi().expandAll(onDone);
  }

  @NotNull
  public ActionCallback cancelUpdate() {
    if (isDisposed()) return ActionCallback.REJECTED;

    return getUi().cancelUpdate();
  }

  @NotNull
  public ActionCallback batch(@NotNull Progressive progressive) {
    if (isDisposed()) return ActionCallback.REJECTED;

    return getUi().batch(progressive);
  }

  @NotNull
  public AsyncResult<Object> revalidateElement(Object element) {
    if (isDisposed()) return AsyncResult.rejected();

    AbstractTreeStructure structure = getTreeStructure();
    if (structure == null) return AsyncResult.rejected();

    return structure.revalidateElement(element);
  }

  public static class AbstractTreeNodeWrapper extends AbstractTreeNode<Object> {
    public AbstractTreeNodeWrapper() {
      super(null, null);
    }

    @Override
    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      return Collections.emptyList();
    }

    @Override
    public void update(PresentationData presentation) {
    }
  }

  public final AbstractTreeUi getUi() {
    return myUi;
  }

  @Override
  public void dispose() {
    if (isDisposed()) return;

    myUi.requestRelease();
  }

  void releaseUi() {
    myUi = null;
  }

  protected boolean updateNodeDescriptor(@NotNull NodeDescriptor descriptor) {
    if (isDisposed()) return false;

    AbstractTreeUi ui = getUi();
    return ui != null && descriptor.update();
  }

  @Nullable
  public final DefaultTreeModel getTreeModel() {
    if (isDisposed()) return null;

    JTree tree = getTree();
    return tree == null ? null : (DefaultTreeModel)tree.getModel();
  }

  @NotNull
  public final Set<Object> getSelectedElements() {
    if (isDisposed()) return Collections.emptySet();

    return getUi().getSelectedElements();
  }

  @NotNull
  public final <T> Set<T> getSelectedElements(@NotNull Class<T> elementClass) {
    Set<T> result = new LinkedHashSet<>();
    for (Object o : getSelectedElements()) {
      Object each = transformElement(o);
      if (elementClass.isInstance(each)) {
        //noinspection unchecked
        result.add((T)each);
      }
    }
    return result;
  }

  protected Object transformElement(Object object) {
    return object;
  }

  public final void setCanYieldUpdate(boolean yield) {
    if (isDisposed()) return;

    getUi().setCanYield(yield);
  }

  @Nullable
  public static AbstractTreeBuilder getBuilderFor(@NotNull JTree tree) {
    final WeakReference ref = (WeakReference)tree.getClientProperty(TREE_BUILDER);
    return (AbstractTreeBuilder)SoftReference.dereference(ref);
  }

  @Nullable
  public final <T> Object accept(@NotNull Class<?> nodeClass, @NotNull TreeVisitor<T> visitor) {
    return accept(nodeClass, getRootElement(), visitor);
  }

  @Nullable
  private <T> Object accept(@NotNull Class<?> nodeClass, Object element, @NotNull TreeVisitor<T> visitor) {
    if (element == null) {
      return null;
    }

    //noinspection unchecked
    if (nodeClass.isAssignableFrom(element.getClass()) && visitor.visit((T)element)) {
      return element;
    }

    final Object[] children = getTreeStructure().getChildElements(element);
    for (Object each : children) {
      final Object childObject = accept(nodeClass, each, visitor);
      if (childObject != null) return childObject;
    }

    return null;
  }

  public <T> boolean select(@NotNull Class nodeClass, @NotNull TreeVisitor<T> visitor, @Nullable Runnable onDone, boolean addToSelection) {
    final Object element = accept(nodeClass, visitor);
    if (element != null) {
      select(element, onDone, addToSelection);
      return true;
    }

    return false;
  }

  public void scrollSelectionToVisible(@Nullable Runnable onDone, boolean shouldBeCentered) {
    if (isDisposed()) return;

    myUi.scrollSelectionToVisible(onDone, shouldBeCentered);
  }

  private static boolean isUnitTestingMode() {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  public static boolean isToPaintSelection(@NotNull JTree tree) {
    AbstractTreeBuilder builder = getBuilderFor(tree);
    return builder == null || builder.getUi() == null || builder.getUi().isToPaintSelection();
  }

  class UserRunnable implements Runnable {
    private final Runnable myRunnable;

    public UserRunnable(Runnable runnable) {
      myRunnable = runnable;
    }

    @Override
    public void run() {
      if (myRunnable != null) {
        AbstractTreeUi ui = getUi();
        if (ui != null) {
          ui.executeUserRunnable(myRunnable);
        }
        else {
          myRunnable.run();
        }
      }
    }
  }

  public boolean isSelectionBeingAdjusted() {
    AbstractTreeUi ui = getUi();
    return ui != null && ui.isSelectionBeingAdjusted();
  }
}
