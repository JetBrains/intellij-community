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
package com.intellij.ide.todo;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.todo.nodes.TodoTreeHelper;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.VisibilityWatcher;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class TodoPanel extends SimpleToolWindowPanel implements OccurenceNavigator, DataProvider, Disposable {
  protected static final Logger LOG = Logger.getInstance(TodoPanel.class);

  protected Project myProject;
  private final TodoPanelSettings mySettings;
  private final boolean myCurrentFileMode;
  private final Content myContent;

  private final Tree myTree;
  private final MyTreeExpander myTreeExpander;
  private final MyOccurenceNavigator myOccurenceNavigator;
  protected final TodoTreeBuilder myTodoTreeBuilder;
  private MyVisibilityWatcher myVisibilityWatcher;
  private UsagePreviewPanel myUsagePreviewPanel;

  /**
   * @param currentFileMode if <code>true</code> then view doesn't have "Group By Packages" and "Flatten Packages"
   *                        actions.
   */
  TodoPanel(Project project, TodoPanelSettings settings, boolean currentFileMode, Content content) {
    super(false, true);

    myProject = project;
    mySettings = settings;
    myCurrentFileMode = currentFileMode;
    myContent = content;

    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);
    myTreeExpander = new MyTreeExpander();
    myOccurenceNavigator = new MyOccurenceNavigator();
    initUI();
    myTodoTreeBuilder = createTreeBuilder(myTree, model, myProject);
    Disposer.register(myProject, myTodoTreeBuilder);
    updateTodoFilter();
    myTodoTreeBuilder.setShowPackages(mySettings.arePackagesShown);
    myTodoTreeBuilder.setShowModules(mySettings.areModulesShown);
    myTodoTreeBuilder.setFlattenPackages(mySettings.areFlattenPackages);

    myVisibilityWatcher = new MyVisibilityWatcher();
    myVisibilityWatcher.install(this);
  }

  protected abstract TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project);

  private void initUI() {
    UIUtil.setLineStyleAngled(myTree);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new TodoCompositeRenderer());
    EditSourceOnDoubleClickHandler.install(myTree);
    new TreeSpeedSearch(myTree);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    PopupHandler.installPopupHandler(myTree, group, ActionPlaces.TODO_VIEW_POPUP, ActionManager.getInstance());

    myTree.addKeyListener(
      new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (!e.isConsumed() && KeyEvent.VK_ENTER == e.getKeyCode()) {
            TreePath path = myTree.getSelectionPath();
            if (path == null) {
              return;
            }
            final Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
            if (!((userObject instanceof NodeDescriptor ? (NodeDescriptor)userObject : null) instanceof TodoItemNode)) {
              return;
            }
            OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(TodoPanel.this), false);
          }
        }
      }
    );


    myUsagePreviewPanel = new UsagePreviewPanel(myProject, FindInProjectUtil.setupViewPresentation(false, new FindModel()));
    Disposer.register(this, myUsagePreviewPanel);
    myUsagePreviewPanel.setVisible(mySettings.showPreview);

    setContent(createCenterComponent());

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        SwingUtilities.invokeLater(() -> {
          if (myUsagePreviewPanel.isVisible()) {
            updatePreviewPanel();
          }
        });
      }
    });


    // Create tool bars and register custom shortcuts

    JPanel toolBarPanel = new JPanel(new GridLayout());

    DefaultActionGroup leftGroup = new DefaultActionGroup();
    leftGroup.add(new PreviousOccurenceToolbarAction(myOccurenceNavigator));
    leftGroup.add(new NextOccurenceToolbarAction(myOccurenceNavigator));
    leftGroup.add(new ContextHelpAction("find.todoList"));
    toolBarPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.TODO_VIEW_TOOLBAR, leftGroup, false).getComponent());

    DefaultActionGroup rightGroup = new DefaultActionGroup();
    AnAction expandAllAction = CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this);
    rightGroup.add(expandAllAction);

    AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this);
    rightGroup.add(collapseAllAction);

    if (!myCurrentFileMode) {
      MyShowModulesAction showModulesAction = new MyShowModulesAction();
      showModulesAction.registerCustomShortcutSet(
        new CustomShortcutSet(
          KeyStroke.getKeyStroke(KeyEvent.VK_M, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)),
        myTree);
      rightGroup.add(showModulesAction);
      MyShowPackagesAction showPackagesAction = new MyShowPackagesAction();
      showPackagesAction.registerCustomShortcutSet(
        new CustomShortcutSet(
          KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)),
        myTree);
      rightGroup.add(showPackagesAction);

      MyFlattenPackagesAction flattenPackagesAction = new MyFlattenPackagesAction();
      flattenPackagesAction.registerCustomShortcutSet(
        new CustomShortcutSet(
          KeyStroke.getKeyStroke(KeyEvent.VK_F, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)),
        myTree);
      rightGroup.add(flattenPackagesAction);
    }

    MyAutoScrollToSourceHandler autoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    autoScrollToSourceHandler.install(myTree);
    rightGroup.add(autoScrollToSourceHandler.createToggleAction());

    SetTodoFilterAction setTodoFilterAction = new SetTodoFilterAction(myProject, mySettings, todoFilter -> setTodoFilter(todoFilter));
    rightGroup.add(setTodoFilterAction);
    rightGroup.add(new MyPreviewAction());
    toolBarPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.TODO_VIEW_TOOLBAR, rightGroup, false).getComponent());

    setToolbar(toolBarPanel);
  }

  protected JComponent createCenterComponent() {
    Splitter splitter = new OnePixelSplitter(false);
    splitter.setSecondComponent(myUsagePreviewPanel);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    return splitter;
  }

  private void updatePreviewPanel() {
    if (myProject.isDisposed()) return;
    List<UsageInfo> infos = new ArrayList<>();
    final TreePath path = myTree.getSelectionPath();
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        Object element = ((NodeDescriptor)userObject).getElement();
        TodoItemNode pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
        if (pointer != null) {
          final SmartTodoItemPointer value = pointer.getValue();
          final Document document = value.getDocument();
          final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
          final RangeMarker rangeMarker = value.getRangeMarker();
          if (psiFile != null) {
            infos.add(new UsageInfo(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset()));
          }
        }
      }
    }
    myUsagePreviewPanel.updateLayout(infos.isEmpty() ? null : infos);
  }

  @Override
  public void dispose() {
    if (myVisibilityWatcher != null) {
      myVisibilityWatcher.deinstall(this);
      myVisibilityWatcher = null;
    }
    myProject = null;
  }

  void rebuildCache() {
    myTodoTreeBuilder.rebuildCache();
  }

  /**
   * Immediately updates tree.
   */
  void updateTree() {
    myTodoTreeBuilder.updateTree(false);
  }

  /**
   * Updates current filter. If previously set filter was removed then empty filter is set.
   *
   * @see TodoTreeBuilder#setTodoFilter
   */
  void updateTodoFilter() {
    TodoFilter filter = TodoConfiguration.getInstance().getTodoFilter(mySettings.todoFilterName);
    setTodoFilter(filter);
  }

  /**
   * Sets specified <code>TodoFilter</code>. The method also updates window's title.
   *
   * @see TodoTreeBuilder#setTodoFilter
   */
  private void setTodoFilter(TodoFilter filter) {
    // Clear name of current filter if it was removed from configuration.
    String filterName = filter != null ? filter.getName() : null;
    mySettings.todoFilterName = filterName;
    // Update filter
    myTodoTreeBuilder.setTodoFilter(filter);
    // Update content's title
    myContent.setDescription(filterName);
  }

  /**
   * @return list of all selected virtual files.
   */
  @Nullable
  protected PsiFile getSelectedFile() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    LOG.assertTrue(node != null);
    if(node.getUserObject() == null){
      return null;
    }
    return TodoTreeBuilder.getFileForNode(node);
  }

  protected void setDisplayName(String tabName) {
    myContent.setDisplayName(tabName);
  }

  @Nullable
  private PsiElement getSelectedElement() {
    if (myTree == null) return null;
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object userObject = node.getUserObject();
    final PsiElement selectedElement = TodoTreeHelper.getInstance(myProject).getSelectedElement(userObject);
    if (selectedElement != null) return selectedElement;
    return getSelectedFile();
  }

  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      Object element = ((NodeDescriptor)userObject).getElement();
      if (!(element instanceof TodoFileNode || element instanceof TodoItemNode)) { // allow user to use F4 only on files an TODOs
        return null;
      }
      TodoItemNode pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
      if (pointer != null) {
        return new OpenFileDescriptor(myProject, pointer.getValue().getTodoItem().getFile().getVirtualFile(),
          pointer.getValue().getRangeMarker().getStartOffset()
        );
      }
      else {
        return null;
      }
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      final PsiFile file = getSelectedFile();
      return file != null ? file.getVirtualFile() : null;
    }
    else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return getSelectedElement();
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      PsiFile file = getSelectedFile();
      if (file != null) {
        return new VirtualFile[]{file.getVirtualFile()};
      }
      else {
        return VirtualFile.EMPTY_ARRAY;
      }
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      //noinspection HardCodedStringLiteral
      return "find.todoList";
    }
    return super.getData(dataId);
  }

  @Override
  @Nullable
  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  @Override
  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  @Override
  @Nullable
  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  protected void rebuildWithAlarm(final Alarm alarm) {
    alarm.cancelAllRequests();
    alarm.addRequest(() -> {
      final Set<VirtualFile> files = new HashSet<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        try {
          myTodoTreeBuilder.collectFiles(virtualFile -> {
            files.add(virtualFile);
            return true;
          });
        }
        catch (IndexNotReadyException ignore) {
        }
      });
      final Runnable runnable = () -> {
        myTodoTreeBuilder.rebuildCache(files);
        updateTree();
      };
      ApplicationManager.getApplication().invokeLater(runnable);
    }, 300);
  }

  private final class MyTreeExpander implements TreeExpander {
    @Override
    public boolean canCollapse() {
      return true;
    }

    @Override
    public boolean canExpand() {
      return true;
    }

    @Override
    public void collapseAll() {
      myTodoTreeBuilder.collapseAll();
    }

    @Override
    public void expandAll() {
      myTodoTreeBuilder.expandAll(null);
    }
  }

  /**
   * Provides support for "auto scroll to source" functionality
   */
  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    MyAutoScrollToSourceHandler() {
    }

    @Override
    protected boolean isAutoScrollMode() {
      return mySettings.isAutoScrollToSource;
    }

    @Override
    protected void setAutoScrollMode(boolean state) {
      mySettings.isAutoScrollToSource = state;
    }
  }

  /**
   * Provides support for "Ctrl+Alt+Up/Down" navigation.
   */
  private final class MyOccurenceNavigator implements OccurenceNavigator {
    @Override
    public boolean hasNextOccurence() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return false;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (userObject == null) {
        return false;
      }
      if (userObject instanceof NodeDescriptor && ((NodeDescriptor)userObject).getElement() instanceof TodoItemNode) {
        return myTree.getRowCount() != myTree.getRowForPath(path) + 1;
      }
      else {
        return node.getChildCount() > 0;
      }
    }

    @Override
    public boolean hasPreviousOccurence() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return false;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      return userObject instanceof NodeDescriptor && !isFirst(node);
    }

    private boolean isFirst(final TreeNode node) {
      final TreeNode parent = node.getParent();
      return parent == null || parent.getIndex(node) == 0 && isFirst(parent);
    }

    @Override
    @Nullable
    public OccurenceInfo goNextOccurence() {
      return goToPointer(getNextPointer());
    }

    @Override
    @Nullable
    public OccurenceInfo goPreviousOccurence() {
      return goToPointer(getPreviousPointer());
    }

    @Override
    public String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.todo");
    }

    @Override
    public String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.todo");
    }

    @Nullable
    private OccurenceInfo goToPointer(TodoItemNode pointer) {
      if (pointer == null) return null;
      myTodoTreeBuilder.select(pointer);
      return new OccurenceInfo(
        new OpenFileDescriptor(myProject, pointer.getValue().getTodoItem().getFile().getVirtualFile(),
                               pointer.getValue().getRangeMarker().getStartOffset()),
        -1,
        -1
      );
    }

    @Nullable
    private TodoItemNode getNextPointer() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      Object element = ((NodeDescriptor)userObject).getElement();
      TodoItemNode pointer;
      if (element instanceof TodoItemNode) {
        pointer = myTodoTreeBuilder.getNextPointer((TodoItemNode)element);
      }
      else {
        pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
      }
      return pointer;
    }

    @Nullable
    private TodoItemNode getPreviousPointer() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      Object element = ((NodeDescriptor)userObject).getElement();
      TodoItemNode pointer;
      if (element instanceof TodoItemNode) {
        pointer = myTodoTreeBuilder.getPreviousPointer((TodoItemNode)element);
      }
      else {
        Object sibling = myTodoTreeBuilder.getPreviousSibling(element);
        if (sibling == null) {
          return null;
        }
        pointer = myTodoTreeBuilder.getLastPointerForElement(sibling);
      }
      return pointer;
    }
  }

  private final class MyShowPackagesAction extends ToggleAction {
    MyShowPackagesAction() {
      super(IdeBundle.message("action.group.by.packages"), null, PlatformIcons.GROUP_BY_PACKAGES);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.arePackagesShown;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.arePackagesShown = state;
      myTodoTreeBuilder.setShowPackages(state);
    }
  }

  private final class MyShowModulesAction extends ToggleAction {
    MyShowModulesAction() {
      super(IdeBundle.message("action.group.by.modules"), null, AllIcons.ObjectBrowser.ShowModules);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.areModulesShown;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.areModulesShown = state;
      myTodoTreeBuilder.setShowModules(state);
    }
  }

  private final class MyFlattenPackagesAction extends ToggleAction {
    MyFlattenPackagesAction() {
      super(IdeBundle.message("action.flatten.packages"), null, PlatformIcons.FLATTEN_PACKAGES_ICON);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      e.getPresentation().setEnabled(mySettings.arePackagesShown);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.areFlattenPackages;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.areFlattenPackages = state;
      myTodoTreeBuilder.setFlattenPackages(state);
    }
  }

  private final class MyVisibilityWatcher extends VisibilityWatcher {
    @Override
    public void visibilityChanged() {
      if (myProject.isOpen()) {
        PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(
          () -> myTodoTreeBuilder.setUpdatable(isShowing()));
      }
    }
  }

  private final class MyPreviewAction extends ToggleAction {

    MyPreviewAction() {
      super("Preview Source", null, AllIcons.Actions.PreviewDetails);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.showPreview;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.showPreview = state;
      myUsagePreviewPanel.setVisible(state);
      if (state) {
        updatePreviewPanel();
      }
    }
  }
}
