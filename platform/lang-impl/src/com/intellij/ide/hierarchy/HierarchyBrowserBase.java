// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;


public abstract class HierarchyBrowserBase extends SimpleToolWindowPanel implements HierarchyBrowser, Disposable, DataProvider {
  protected final Project myProject;
  protected Content myContent;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private volatile boolean myDisposed;

  protected HierarchyBrowserBase(@NotNull Project project) {
    super(true, true);
    myProject = project;
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getSettings(myProject).IS_AUTOSCROLL_TO_SOURCE;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        HierarchyBrowserManager.getSettings(myProject).IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };
  }

  @Override
  public void setContent(@NotNull Content content) {
    myContent = content;
  }

  protected void buildUi(@NotNull JComponent toolbar, @NotNull JComponent content) {
    setToolbar(toolbar);
    setContent(content);
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  protected ActionToolbar createToolbar(@NotNull String place, @NotNull String helpID) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    appendActions(actionGroup, helpID);
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(place, actionGroup, true);
    actionToolbar.setTargetComponent(this);
    return actionToolbar;
  }

  protected void appendActions(@NotNull DefaultActionGroup actionGroup, @Nullable String helpID) {
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    ActionManager actionManager = ActionManager.getInstance();
    actionGroup.add(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL));
    actionGroup.add(actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL));
    actionGroup.add(actionManager.getAction(PinToolwindowTabAction.ACTION_NAME));
    actionGroup.add(CommonActionsManager.getInstance().createExportToTextFileAction(new ExporterToTextFileHierarchy(this)));
    actionGroup.add(new CloseAction());
  }

  protected abstract JTree getCurrentTree();

  abstract StructureTreeModel getCurrentBuilder();

  @Nullable
  protected abstract PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor);

  @Nullable
  protected final PsiElement getSelectedElement(@NotNull DataContext dataContext) {
    Object element = ArrayUtil.getFirstElement(dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS));
    if (!(element instanceof HierarchyNodeDescriptor)) return null;
    return getElementFromDescriptor((HierarchyNodeDescriptor)element);
  }

  @Nullable
  protected static HierarchyNodeDescriptor getDescriptor(@NotNull DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (userObject instanceof HierarchyNodeDescriptor) {
      return (HierarchyNodeDescriptor)userObject;
    }
    return null;
  }

  public PsiElement @NotNull [] getAvailableElements() {
    JTree tree = getCurrentTree();
    if (tree == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    if (!(root instanceof DefaultMutableTreeNode)) {
      return PsiElement.EMPTY_ARRAY;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)root;
    HierarchyNodeDescriptor descriptor = getDescriptor(node);
    Set<PsiElement> result = new HashSet<>();
    if (descriptor != null) {
      collectElements(descriptor, result);
    }
    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  private void collectElements(@NotNull HierarchyNodeDescriptor descriptor, @NotNull Set<? super PsiElement> out) {
    PsiElement element = getElementFromDescriptor(descriptor);
    if (element != null) {
      out.add(element.getNavigationElement());
    }
    Object[] children = descriptor.getCachedChildren();
    if (children == null) {
      return;
    }
    for (Object child : children) {
      if (child instanceof HierarchyNodeDescriptor) {
        HierarchyNodeDescriptor childDescriptor = (HierarchyNodeDescriptor)child;
        collectElements(childDescriptor, out);
      }
    }
  }

  public final HierarchyNodeDescriptor @NotNull [] getSelectedDescriptors() {
    JTree tree = getCurrentTree();
    if (tree == null) {
      return HierarchyNodeDescriptor.EMPTY_ARRAY;
    }
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return HierarchyNodeDescriptor.EMPTY_ARRAY;
    }
    ArrayList<HierarchyNodeDescriptor> list = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        HierarchyNodeDescriptor descriptor = getDescriptor(node);
        if (descriptor != null) {
          list.add(descriptor);
        }
      }
    }
    return list.toArray(HierarchyNodeDescriptor.EMPTY_ARRAY);
  }

  protected PsiElement @NotNull [] getSelectedElements() {
    HierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
    ArrayList<PsiElement> elements = new ArrayList<>();
    for (HierarchyNodeDescriptor descriptor : descriptors) {
      PsiElement element = getElementFromDescriptor(descriptor);
      if (element != null) elements.add(element);
    }
    return PsiUtilCore.toPsiElementArray(elements);
  }


  private Navigatable getNavigatable(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof Navigatable && descriptor.isValid()) {
      return (Navigatable)descriptor;
    }

    PsiElement element = getElementFromDescriptor(descriptor);
    if (element instanceof NavigatablePsiElement && element.isValid()) {
      return (NavigatablePsiElement)element;
    }
    return null;
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return null;
    }
    if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      JTree tree = getCurrentTree();
      if (tree != null) {
        return new DefaultTreeExpander(tree);
      }
    }
    if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)) {
      return getSelectedDescriptors();
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      DataProvider bgtProvider = (DataProvider)super.getData(PlatformCoreDataKeys.BGT_DATA_PROVIDER.getName());
      HierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
      return CompositeDataProvider.compose(slowId -> getSlowData(slowId, descriptors), bgtProvider);
    }
    return super.getData(dataId);
  }

  protected @Nullable Object getSlowData(@NotNull String dataId, HierarchyNodeDescriptor @NotNull [] selection) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      PsiElement anElement = selection.length > 0 ? getElementFromDescriptor(selection[0]) : null;
      return anElement != null && anElement.isValid() ? anElement : null;
    }
    if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      return JBIterable.of(selection).filterMap(this::getElementFromDescriptor).toArray(PsiElement.EMPTY_ARRAY);
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      HierarchyNodeDescriptor descriptor = selection.length > 0 ? selection[0] : null;
      if (descriptor == null) return null;
      return getNavigatable(descriptor);
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return JBIterable.of(selection).filterMap(this::getNavigatable).toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    }
    return null;
  }

  private final class CloseAction extends CloseTabToolbarAction {
    private CloseAction() {
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Objects.requireNonNull(myContent.getManager()).removeContent(myContent, true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(myContent != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  protected void configureTree(@NotNull Tree tree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    myAutoScrollToSourceHandler.install(tree);
  }
}