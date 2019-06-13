// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.reference.SoftReference;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @deprecated use {@link com.intellij.ui.tree.AsyncTreeModel} and {@link com.intellij.ui.tree.StructureTreeModel} instead.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public class AbstractTreeBuilder implements Disposable {
  private AbstractTreeUi myUi;
  @NonNls private static final String TREE_BUILDER = "TreeBuilder";
  protected static final boolean DEFAULT_UPDATE_INACTIVE = true;

  public AbstractTreeBuilder(@NotNull JTree tree,
                             @NotNull DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure,
                             @Nullable Comparator<? super NodeDescriptor> comparator) {
    this(tree, treeModel, treeStructure, comparator, DEFAULT_UPDATE_INACTIVE);
  }

  public AbstractTreeBuilder(@NotNull JTree tree,
                             @NotNull DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure,
                             @Nullable Comparator<? super NodeDescriptor> comparator,
                             boolean updateIfInactive) {
    init(tree, treeModel, treeStructure, comparator, updateIfInactive);
  }

  protected AbstractTreeBuilder() {

  }

  protected void init(@NotNull JTree tree,
                      @NotNull DefaultTreeModel treeModel,
                      AbstractTreeStructure treeStructure,
                      @Nullable final Comparator<? super NodeDescriptor> comparator,
                      final boolean updateIfInactive) {

    tree.putClientProperty(TREE_BUILDER, new WeakReference<>(this));

    myUi = createUi();
    myUi.init(this, tree, treeModel, treeStructure, comparator, updateIfInactive);

    setPassthroughMode(isUnitTestingMode());
  }

  @NotNull
  protected AbstractTreeUi createUi() {
    return new AbstractTreeUi();
  }

  public final void scrollTo(Object element) {
    scrollTo(element, null);
  }

  public final void scrollTo(Object element, Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.userScrollTo(element, onDone == null ? null : new UserRunnable(onDone));
  }

  public final void select(final Object element) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.userSelect(new Object[]{element}, null, false, true);
  }

  public final void select(final Object element, @Nullable final Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.userSelect(new Object[]{element}, new UserRunnable(onDone), false, true);
  }

  public final void select(final Object element, @Nullable final Runnable onDone, boolean addToSelection) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.userSelect(new Object[]{element}, new UserRunnable(onDone), addToSelection, true);
  }

  public final void select(final Object[] elements, @Nullable final Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.userSelect(elements, new UserRunnable(onDone), false, true);
  }

  public final void select(final Object[] elements, @Nullable final Runnable onDone, boolean addToSelection) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.userSelect(elements, new UserRunnable(onDone), addToSelection, true);
  }

  public final void expand(Object element, @Nullable Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.expand(element, new UserRunnable(onDone));
  }

  public final void expand(Object[] element, @Nullable Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.expand(element, new UserRunnable(onDone));
  }

  public final void collapseChildren(Object element, @Nullable Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.collapseChildren(element, new UserRunnable(onDone));
  }


  @NotNull
  static AbstractTreeNode<Object> createSearchingTreeNodeWrapper() {
    return new AbstractTreeNodeWrapper();
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
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.getUpdater();
  }

  public final boolean addSubtreeToUpdateByElement(@NotNull Object element) {
    AbstractTreeUpdater updater = getUpdater();
    return updater != null && updater.addSubtreeToUpdateByElement(element);
  }

  public final void addSubtreeToUpdate(DefaultMutableTreeNode node) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.addSubtreeToUpdate(node);
  }

  public final void addSubtreeToUpdate(DefaultMutableTreeNode node, Runnable afterUpdate) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.addSubtreeToUpdate(node, afterUpdate);
  }

  @Nullable
  public final DefaultMutableTreeNode getRootNode() {
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.getRootNode();
  }

  public final void setNodeDescriptorComparator(Comparator<? super NodeDescriptor> nodeDescriptorComparator) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.setNodeDescriptorComparator(nodeDescriptorComparator);
  }

  /**
   * node descriptor getElement contract is as follows:
   * 1.TreeStructure always returns & receives "treeStructure" element returned by getTreeStructureElement
   * 2.Paths contain "model" element returned by getElement
   */
  protected Object getTreeStructureElement(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor == null ? null : nodeDescriptor.getElement();
  }


  protected void updateNode(final DefaultMutableTreeNode node) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.doUpdateNode(node);
  }

  protected boolean validateNode(@NotNull Object child) {
    AbstractTreeStructure structure = getTreeStructure();
    return structure != null && structure.isValid(child);
  }

  protected boolean isDisposeOnCollapsing(NodeDescriptor nodeDescriptor) {
    return true;
  }

  public final JTree getTree() {
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.getTree();
  }

  public final AbstractTreeStructure getTreeStructure() {
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.getTreeStructure();
  }

  public final void setTreeStructure(@NotNull AbstractTreeStructure structure) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.setTreeStructure(structure);
  }

  @Nullable
  public Object getRootElement() {
    AbstractTreeStructure structure = getTreeStructure();
    return structure == null ? null : structure.getRootElement();
  }

  /**
   * @deprecated use {@link #queueUpdate()}
   */
  @Deprecated
  public void updateFromRoot() {
    queueUpdate();
  }

  public void initRootNode() {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.initRootNode();
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
    AbstractTreeUi ui = getUi();
    if (ui == null) {
      return ActionCallback.REJECTED;
    }

    final ActionCallback result = new ActionCallback();
    ui.invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeBuilder.queueUpdateFrom") {
      @Override
      public void perform() {
        AbstractTreeUi ui = getUi();
        if (ui == null) {
          result.reject("ui is null");
          return;
        }

        if (updateStructure && forceResort) {
          ui.incComparatorStamp();
        }
        ui.queueUpdate(element, updateStructure).notify(result);
      }
    });
    return result;
  }

  /**
   * @deprecated use {@link AbstractTreeUi#buildNodeForElement(Object)}
   */
  @Deprecated
  public void buildNodeForElement(@NotNull Object element) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.buildNodeForElement(element);
  }

  /**
   * @deprecated use {@link AbstractTreeUi#getNodeForElement(Object, boolean)}
   */
  @Deprecated
  @Nullable
  public DefaultMutableTreeNode getNodeForElement(@NotNull Object element) {
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.getNodeForElement(element, false);
  }

  public void cleanUp() {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.doCleanUp();
  }

  @Nullable
  protected ProgressIndicator createProgressIndicator() {
    return null;
  }

  protected void expandNodeChildren(@NotNull DefaultMutableTreeNode node) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.doExpandNodeChildren(node);
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
    return getUi() == null;
  }

  final boolean wasRootNodeInitialized() {
    AbstractTreeUi ui = getUi();
    return ui != null && ui.wasRootNodeInitialized();
  }

  public final boolean isNodeBeingBuilt(final TreePath path) {
    AbstractTreeUi ui = getUi();
    return ui != null && ui.isNodeBeingBuilt(path);
  }

  @Nullable
  protected Object findNodeByElement(@NotNull Object element) {
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.findNodeByElement(element);
  }

  public static boolean isLoadingNode(final DefaultMutableTreeNode node) {
    return AbstractTreeUi.isLoadingNode(node);
  }

  void runOnYieldingDone(@NotNull Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui == null) return;

    if (ui.isPassthroughMode() || SwingUtilities.isEventDispatchThread()) {
      onDone.run();
    }
    else {
      EdtExecutorService.getInstance().execute(()->{
        if (!isDisposed()) onDone.run();
      });
    }
  }

  protected void yield(@NotNull Runnable runnable) {
    AbstractTreeUi ui = getUi();
    if (ui == null) return;

    if (ui.isPassthroughMode()) {
      runnable.run();
    }
    else {
      EdtExecutorService.getInstance().execute(()->{
        if (!isDisposed()) runnable.run();
      });
    }
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
    AbstractTreeUi ui = getUi();
    if (ui == null) return;

    if (ui.isPassthroughMode()) {
      runnable.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
  }

  @NotNull
  public final ActionCallback getInitialized() {
    AbstractTreeUi ui = getUi();
    return ui == null ? ActionCallback.REJECTED : ui.getInitialized();
  }

  @NotNull
  public final ActionCallback getReady(Object requestor) {
    AbstractTreeUi ui = getUi();
    return ui == null ? ActionCallback.REJECTED : ui.getReady(requestor);
  }

  protected void sortChildren(Comparator<? super TreeNode> nodeComparator, DefaultMutableTreeNode node, List<? extends TreeNode> children) {
    Collections.sort(children, nodeComparator);
  }

  public void setPassthroughMode(boolean passthrough) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.setPassthroughMode(passthrough);
  }

  public void expandAll(@Nullable Runnable onDone) {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.expandAll(onDone);
  }

  @NotNull
  public ActionCallback cancelUpdate() {
    AbstractTreeUi ui = getUi();
    return ui == null ? ActionCallback.REJECTED : ui.cancelUpdate();
  }

  @NotNull
  public ActionCallback batch(@NotNull Progressive progressive) {
    AbstractTreeUi ui = getUi();
    return ui == null ? ActionCallback.REJECTED : ui.batch(progressive);
  }

  @NotNull
  public Promise<Object> revalidateElement(@NotNull Object element) {
    AbstractTreeStructure structure = getTreeStructure();
    if (structure == null) {
      return Promises.rejectedPromise();
    }

    AsyncPromise<Object> promise = new AsyncPromise<>();
    structure
      .revalidateElement(element)
      .doWhenDone((Consumer<Object>)o -> promise.setResult(o))
      .doWhenRejected(s -> promise.setError(s));
    return promise;
  }

  private static class AbstractTreeNodeWrapper extends AbstractTreeNode<Object> {
    AbstractTreeNodeWrapper() {
      super(null, TREE_WRAPPER_VALUE);
    }

    @Override
    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      return Collections.emptyList();
    }

    @Override
    public void update(@NotNull PresentationData presentation) {
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) return true;
      // this hack allows to find a node in a map without checking a class type
      return object instanceof AbstractTreeNode && Comparing.equal(getEqualityObject(), ((AbstractTreeNode)object).getEqualityObject());
    }
  }

  public final AbstractTreeUi getUi() {
    AbstractTreeUi ui = myUi;
    return ui == null || ui.isReleaseRequested() ? null : ui;
  }

  @Override
  public void dispose() {
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.requestRelease();
  }

  void releaseUi() {
    myUi = null;
  }

  protected boolean updateNodeDescriptor(@NotNull NodeDescriptor descriptor) {
    AbstractTreeUi ui = getUi();
    return ui != null && descriptor.update();
  }

  @Nullable
  public final DefaultTreeModel getTreeModel() {
    AbstractTreeUi ui = getUi();
    return ui == null ? null : ui.getTreeModel();
  }

  @NotNull
  public final Set<Object> getSelectedElements() {
    AbstractTreeUi ui = getUi();
    return ui == null ? Collections.emptySet() : ui.getSelectedElements();
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
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.setCanYield(yield);
  }

  @Nullable
  public static AbstractTreeBuilder getBuilderFor(@NotNull JTree tree) {
    Reference<AbstractTreeBuilder> ref = (Reference)tree.getClientProperty(TREE_BUILDER);
    return SoftReference.dereference(ref);
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

    AbstractTreeStructure structure = getTreeStructure();
    if (structure == null) return null;

    //noinspection unchecked
    if (nodeClass.isAssignableFrom(element.getClass()) && visitor.visit((T)element)) {
      return element;
    }

    final Object[] children = structure.getChildElements(element);
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
    AbstractTreeUi ui = getUi();
    if (ui != null) ui.scrollSelectionToVisible(onDone, shouldBeCentered);
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

    UserRunnable(Runnable runnable) {
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

  public boolean isToBuildChildrenInBackground(Object element) {
    AbstractTreeUi ui = getUi();
    return ui != null && ui.isToBuildChildrenInBackground(element);
  }

  public final boolean isConsistent() {
    AbstractTreeUi ui = getUi();
    return ui != null && ui.isConsistent();
  }
}
