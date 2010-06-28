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

package com.intellij.ide.hierarchy;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
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
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class HierarchyBrowserBase extends SimpleToolWindowPanel implements HierarchyBrowser, Disposable, DataProvider {
  private static final HierarchyNodeDescriptor[] EMPTY_DESCRIPTORS = new HierarchyNodeDescriptor[0];

  protected Content myContent;
  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  protected final Project myProject;

  protected HierarchyBrowserBase(Project project) {
    super(true, true);
    myProject = project;
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {

      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getInstance(myProject).getState().IS_AUTOSCROLL_TO_SOURCE;
      }

      protected void setAutoScrollMode(final boolean state) {
        HierarchyBrowserManager.getInstance(myProject).getState().IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };
  }

  public void setContent(final Content content) {
    myContent = content;
  }

  public JComponent getComponent() {
    return this;
  }

  protected void buildUi(JComponent toolbar, JComponent content) {
    setToolbar(toolbar);
    setContent(content);
  }

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
    actionGroup.add(PinToolwindowTabAction.getPinAction());
    actionGroup.add(new CloseAction());
    if (helpID != null) {
      actionGroup.add(new ContextHelpAction(helpID));
    }
  }

  protected abstract JTree getCurrentTree();

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

  private PsiElement[] getSelectedElements() {
    HierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
    ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
    for (HierarchyNodeDescriptor descriptor : descriptors) {
      PsiElement element = getElementFromDescriptor(descriptor);
      if (element != null) elements.add(element);
    }
    return elements.toArray(new PsiElement[elements.size()]);
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

  @Nullable
  public Object getData(@NonNls final String dataId) {
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement anElement = getSelectedElement();
      return anElement != null && anElement.isValid() ? anElement : super.getData(dataId);
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      return getSelectedElements();
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return null;
    }
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      final DefaultMutableTreeNode selectedNode = getSelectedNode();
      if (selectedNode == null) return null;
      final HierarchyNodeDescriptor descriptor = getDescriptor(selectedNode);
      if (descriptor == null) return null;
      return getNavigatable(descriptor);
    }
    if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getNavigatables();
    }
    return super.getData(dataId);
  }

  private final class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      myContent.getManager().removeContent(myContent, true);
    }
  }

  protected void configureTree(Tree tree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    UIUtil.setLineStyleAngled(tree);
    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    myAutoScrollToSourceHandler.install(tree);
  }

}
