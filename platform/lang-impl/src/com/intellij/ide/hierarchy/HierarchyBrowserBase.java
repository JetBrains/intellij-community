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

package com.intellij.ide.hierarchy;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
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
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public abstract class HierarchyBrowserBase extends SimpleToolWindowPanel implements HierarchyBrowser, Disposable, DataProvider {
  private static final HierarchyNodeDescriptor[] EMPTY_DESCRIPTORS = new HierarchyNodeDescriptor[0];

  protected Content myContent;
  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  protected final Project myProject;

  protected HierarchyBrowserBase(@NotNull Project project) {
    super(true, true);
    myProject = project;
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getInstance(myProject).getState().IS_AUTOSCROLL_TO_SOURCE;
      }

      @Override
      protected void setAutoScrollMode(final boolean state) {
        HierarchyBrowserManager.getInstance(myProject).getState().IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };
  }

  @Override
  public void setContent(final Content content) {
    myContent = content;
  }

  protected void buildUi(JComponent toolbar, JComponent content) {
    setToolbar(toolbar);
    setContent(content);
  }

  @Override
  public void dispose() {
  }

  protected ActionToolbar createToolbar(final String place, final String helpID) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    appendActions(actionGroup, helpID);
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(place, actionGroup, true);
    actionToolbar.setTargetComponent(this);
    return actionToolbar;
  }

  protected void appendActions(@NotNull DefaultActionGroup actionGroup, @Nullable String helpID) {
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    ActionManager actionManager = ActionManager.getInstance();
    actionGroup.add(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL));
    actionGroup.add(actionManager.getAction(PinToolwindowTabAction.ACTION_NAME));
    actionGroup.add(CommonActionsManager.getInstance().createExportToTextFileAction(new ExporterToTextFileHierarchy(this)));
    actionGroup.add(new CloseAction());
    if (helpID != null) {
      actionGroup.add(new ContextHelpAction(helpID));
    }
  }

  protected abstract JTree getCurrentTree();
  protected abstract HierarchyTreeBuilder getCurrentBuilder();

  @Nullable
  protected abstract PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor);

  @Nullable
  protected DefaultMutableTreeNode getSelectedNode() {
    final JTree tree = getCurrentTree();
    if (tree == null) return null;
    final TreePath path = tree.getSelectionPath();
    if (path == null) return null;
    final Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return null;
    return (DefaultMutableTreeNode)lastPathComponent;
  }

  @Nullable
  protected final PsiElement getSelectedElement() {
    final DefaultMutableTreeNode node = getSelectedNode();
    final HierarchyNodeDescriptor descriptor = node != null ? getDescriptor(node) : null;
    return descriptor != null ? getElementFromDescriptor(descriptor) : null;
  }

  @Nullable
  protected HierarchyNodeDescriptor getDescriptor(DefaultMutableTreeNode node) {
    final Object userObject = node != null ? node.getUserObject() : null;
    if (userObject instanceof HierarchyNodeDescriptor) {
      return (HierarchyNodeDescriptor)userObject;
    }
    return null;
  }

  public PsiElement[] getAvailableElements() {
    final JTree tree = getCurrentTree();
    if (tree == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    final TreeModel model = tree.getModel();
    final Object root = model.getRoot();
    if (!(root instanceof DefaultMutableTreeNode)) {
      return PsiElement.EMPTY_ARRAY;
    }
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)root;
    final HierarchyNodeDescriptor descriptor = getDescriptor(node);
    final Set<PsiElement> result = new HashSet<PsiElement>();
    collectElements(descriptor, result);
    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  private void collectElements(HierarchyNodeDescriptor descriptor, Set<PsiElement> out) {
    if (descriptor == null) {
      return;
    }
    final PsiElement element = getElementFromDescriptor(descriptor);
    if (element != null) {
      out.add(element.getNavigationElement());
    }
    final Object[] children = descriptor.getCachedChildren();
    if (children == null) {
      return;
    }
    for (Object child : children) {
      if (child instanceof HierarchyNodeDescriptor) {
        final HierarchyNodeDescriptor childDescriptor = (HierarchyNodeDescriptor)child;
        collectElements(childDescriptor, out);
      }
    }
  }

  public final HierarchyNodeDescriptor[] getSelectedDescriptors() {
    final JTree tree = getCurrentTree();
    if (tree == null) {
      return EMPTY_DESCRIPTORS;
    }
    final TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return EMPTY_DESCRIPTORS;
    }
    final ArrayList<HierarchyNodeDescriptor> list = new ArrayList<HierarchyNodeDescriptor>(paths.length);
    for (final TreePath path : paths) {
      final Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        HierarchyNodeDescriptor descriptor = getDescriptor(node);
        if (descriptor != null) {
          list.add(descriptor);
        }
      }
    }
    return list.toArray(new HierarchyNodeDescriptor[list.size()]);
  }

  @NotNull
  protected PsiElement[] getSelectedElements() {
    HierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
    ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
    for (HierarchyNodeDescriptor descriptor : descriptors) {
      PsiElement element = getElementFromDescriptor(descriptor);
      if (element != null) elements.add(element);
    }
    return PsiUtilCore.toPsiElementArray(elements);
  }


  private Navigatable[] getNavigatables() {
    final HierarchyNodeDescriptor[] selectedDescriptors = getSelectedDescriptors();
    if (selectedDescriptors == null || selectedDescriptors.length == 0) return null;
    final ArrayList<Navigatable> result = new ArrayList<Navigatable>();
    for (HierarchyNodeDescriptor descriptor : selectedDescriptors) {
      Navigatable navigatable = getNavigatable(descriptor);
      if (navigatable != null) {
        result.add(navigatable);
      }
    }
    return result.toArray(new Navigatable[result.size()]);
  }

  private Navigatable getNavigatable(HierarchyNodeDescriptor descriptor) {
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
  public Object getData(@NonNls final String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement anElement = getSelectedElement();
      return anElement != null && anElement.isValid() ? anElement : super.getData(dataId);
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      return getSelectedElements();
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return null;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final DefaultMutableTreeNode selectedNode = getSelectedNode();
      if (selectedNode == null) return null;
      final HierarchyNodeDescriptor descriptor = getDescriptor(selectedNode);
      if (descriptor == null) return null;
      return getNavigatable(descriptor);
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getNavigatables();
    }
    if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      final JTree tree = getCurrentTree();
      if (tree != null) {
        return new DefaultTreeExpander(tree);
      }
    }
    return super.getData(dataId);
  }

  private final class CloseAction extends CloseTabToolbarAction {
    private CloseAction() {
    }

    @Override
    public final void actionPerformed(final AnActionEvent e) {
      final HierarchyTreeBuilder builder = getCurrentBuilder();
      final AbstractTreeUi treeUi = builder != null ? builder.getUi() : null;
      final ProgressIndicator progress = treeUi != null ? treeUi.getProgress() : null;
      if (progress != null) {
        progress.cancel();
      }
      myContent.getManager().removeContent(myContent, true);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(myContent != null);
    }
  }

  protected void configureTree(@NotNull Tree tree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    UIUtil.setLineStyleAngled(tree);
    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    myAutoScrollToSourceHandler.install(tree);
  }

}
