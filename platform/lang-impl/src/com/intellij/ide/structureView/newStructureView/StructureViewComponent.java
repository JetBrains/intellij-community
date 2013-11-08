/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.impl.StructureViewState;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.ide.util.treeView.smartTree.TreeModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StructureViewComponent extends SimpleToolWindowPanel implements TreeActionsOwner, DataProvider, StructureView.Scrollable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.structureView.newStructureView.StructureViewComponent");
  @NonNls private static final String ourHelpID = "viewingStructure.fileStructureView";

  private AbstractTreeBuilder myAbstractTreeBuilder;

  private FileEditor myFileEditor;
  private final TreeModelWrapper myTreeModelWrapper;

  private StructureViewState myStructureViewState;
  private boolean myAutoscrollFeedback;

  private final Alarm myAutoscrollAlarm = new Alarm();

  private final CopyPasteDelegator myCopyPasteDelegator;
  private final MyAutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final AutoScrollFromSourceHandler myAutoScrollFromSourceHandler;

  private static final Key<StructureViewState> STRUCTURE_VIEW_STATE_KEY = Key.create("STRUCTURE_VIEW_STATE");
  private final Project myProject;
  private final StructureViewModel myTreeModel;
  private static int ourSettingsModificationCount;

  public StructureViewComponent(FileEditor editor, StructureViewModel structureViewModel, Project project) {
    this(editor, structureViewModel, project, true);
  }

  public StructureViewComponent(final FileEditor editor,
                                final StructureViewModel structureViewModel,
                                final Project project,
                                final boolean showRootNode) {
    super(true, true);

    myProject = project;
    myFileEditor = editor;
    myTreeModel = structureViewModel;
    myTreeModelWrapper = new TreeModelWrapper(myTreeModel, this);

    SmartTreeStructure treeStructure = new SmartTreeStructure(project, myTreeModelWrapper){
      @Override
      public void rebuildTree() {
        if (!isDisposed()) {
          super.rebuildTree();
        }
      }

      @Override
      public boolean isToBuildChildrenInBackground(final Object element) {
        return getRootElement() == element;
      }

      @Override
      protected TreeElementWrapper createTree() {
        return new StructureViewTreeElementWrapper(myProject, myModel.getRoot(), myModel);
      }

      @Override
      public String toString() {
        return "structure view tree structure(model=" + myTreeModel + ")";
      }
    };

    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(treeStructure.getRootElement()));
    JTree tree = new JBTreeWithHintProvider(model);
    tree.setRootVisible(showRootNode);
    tree.setShowsRootHandles(true);

    myAbstractTreeBuilder = new StructureTreeBuilder(project, tree,
                                                     (DefaultTreeModel)tree.getModel(),treeStructure,myTreeModelWrapper) {
      @Override
      protected boolean validateNode(Object child) {
        return isValid(child);
      }
    };
    Disposer.register(this, myAbstractTreeBuilder);
    Disposer.register(myAbstractTreeBuilder, new Disposable() {
      @Override
      public void dispose() {
        storeState();
      }
    });

    setContent(ScrollPaneFactory.createScrollPane(myAbstractTreeBuilder.getTree()));

    myAbstractTreeBuilder.getTree().setCellRenderer(new NodeRenderer());

    myAutoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler(myProject, this);

    if (getSettings().SHOW_TOOLBAR) {
      setToolbar(createToolbar());
    }

    installTree();

    myCopyPasteDelegator = new CopyPasteDelegator(myProject, getTree()) {
      @Override
      @NotNull
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
  }

  public void showToolbar() {
    setToolbar(createToolbar());
  }

  private JComponent createToolbar() {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.STRUCTURE_VIEW_TOOLBAR, createActionGroup(), true).getComponent();
  }

  private void installTree() {
    getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myAutoScrollToSourceHandler.install(getTree());
    myAutoScrollFromSourceHandler.install();

    TreeUtil.installActions(getTree());

    new TreeSpeedSearch(getTree(), new Convertor<TreePath, String>() {
      @Override
      public String convert(final TreePath treePath) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject != null) {
          return FileStructurePopup.getSpeedSearchText(userObject);
        }
        return null;
      }
    });

    addTreeKeyListener();
    addTreeMouseListeners();
    restoreState();
  }

  private PsiElement[] getSelectedPsiElements() {
    return filterPsiElements(getSelectedElements());
  }

  @NotNull
  private static PsiElement[] filterPsiElements(Object[] selectedElements) {
    if (selectedElements == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();

    for (Object selectedElement : selectedElements) {
      if (selectedElement instanceof PsiElement) {
        psiElements.add((PsiElement)selectedElement);
      }
    }
    return PsiUtilCore.toPsiElementArray(psiElements);
  }

  private Object[] getSelectedElements() {
    final JTree tree = getTree();
    return tree != null ? convertPathsToValues(tree.getSelectionPaths()): ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  private Object[] getSelectedTreeElements() {
    final JTree tree = getTree();
    return tree != null ? convertPathsToTreeElements(tree.getSelectionPaths()) : null;
  }


  private static Object[] convertPathsToValues(TreePath[] selectionPaths) {
    if (selectionPaths == null) return null;
    List<Object> result = new ArrayList<Object>();
    for (TreePath selectionPath : selectionPaths) {
      ContainerUtil.addIfNotNull(result, getNodeTreeValue((DefaultMutableTreeNode)selectionPath.getLastPathComponent()));
    }
    return ArrayUtil.toObjectArray(result);
  }

  @Nullable
  private static Object[] convertPathsToTreeElements(TreePath[] selectionPaths) {
    if (selectionPaths == null) return null;
    List<Object> result = new ArrayList<Object>();
    for (TreePath selectionPath : selectionPaths) {
      ContainerUtil.addIfNotNull(result, getNodeValue((DefaultMutableTreeNode)selectionPath.getLastPathComponent()));
    }
    return ArrayUtil.toObjectArray(result);
  }

  @Nullable
  private static Object getNodeValue(DefaultMutableTreeNode mutableTreeNode) {
    Object userObject = mutableTreeNode.getUserObject();
    if (userObject instanceof FilteringTreeStructure.FilteringNode) {
      userObject = ((FilteringTreeStructure.FilteringNode)userObject).getDelegate();
    }
    return userObject instanceof AbstractTreeNode ? ((AbstractTreeNode)userObject).getValue() : null;
  }

  @Nullable
  private static Object getNodeTreeValue(DefaultMutableTreeNode mutableTreeNode) {
    Object value = getNodeValue(mutableTreeNode);
    return value instanceof StructureViewTreeElement ? ((StructureViewTreeElement)value).getValue() : null;
  }

  private void addTreeMouseListeners() {
    EditSourceOnDoubleClickHandler.install(getTree());
    CustomizationUtil.installPopupHandler(getTree(), IdeActions.GROUP_STRUCTURE_VIEW_POPUP, ActionPlaces.STRUCTURE_VIEW_POPUP);
  }

  private void addTreeKeyListener() {
    getTree().addKeyListener(
        new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER == e.getKeyCode()) {
            DataContext dataContext = DataManager.getInstance().getDataContext(getTree());
            OpenSourceUtil.openSourcesFrom(dataContext, false);
          }
          else if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
            if (e.isConsumed())
            {
              return;
            }
            PsiCopyPasteManager copyPasteManager = PsiCopyPasteManager.getInstance();
            boolean[] isCopied = new boolean[1];
            if (copyPasteManager.getElements(isCopied) != null && !isCopied[0]) {
              copyPasteManager.clear();
              e.consume();
            }
          }
        }
      });
  }

  @Override
  public void storeState() {
    if (!isDisposed()) {
      myStructureViewState = getState();
      myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, myStructureViewState);
    }
  }

  public StructureViewState getState() {
    StructureViewState structureViewState = new StructureViewState();
    if (getTree() != null) {
      structureViewState.setExpandedElements(getExpandedElements());
      structureViewState.setSelectedElements(getSelectedElements());
    }
    return structureViewState;
  }

  private Object[] getExpandedElements() {
    final JTree tree = getTree();
    if (tree == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(tree);
    return convertPathsToValues(expandedPaths.toArray(new TreePath[expandedPaths.size()]));
  }


  @Override
  public void restoreState() {
    myStructureViewState = myFileEditor.getUserData(STRUCTURE_VIEW_STATE_KEY);
    if (myStructureViewState == null) {
      TreeUtil.expand(getTree(), 2);
    }
    else {
      expandStoredElements();
      selectStoredElements();
      myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, null);
      myStructureViewState = null;
    }
  }

  private void selectStoredElements() {
    Object[] selectedPsiElements = null;

    if (myStructureViewState != null) {
      selectedPsiElements = myStructureViewState.getSelectedElements();
    }

    if (selectedPsiElements == null) {
      getTree().setSelectionPath(new TreePath(getRootNode().getPath()));
    }
    else {
      for (Object element : selectedPsiElements) {
        if (element instanceof PsiElement && !((PsiElement)element).isValid()) {
          continue;
        }
        addSelectionPathTo(element);
      }
    }
  }

  public void addSelectionPathTo(final Object element) {
    DefaultMutableTreeNode node = myAbstractTreeBuilder.getNodeForElement(element);
    if (node != null) {
      final JTree tree = getTree();
      final TreePath path = new TreePath(node.getPath());
      if (node == tree.getModel().getRoot() && !tree.isExpanded(path)) tree.expandPath(path);
      tree.addSelectionPath(path);
    }
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTree().getModel().getRoot();
  }

  private void expandStoredElements() {
    Object[] expandedPsiElements = null;

    if (myStructureViewState != null) {
      expandedPsiElements = myStructureViewState.getExpandedElements();
    }

    if (expandedPsiElements == null) {
      getTree().expandPath(new TreePath(getRootNode().getPath()));
    }
    else {
      for (Object element : expandedPsiElements) {
        if (element instanceof PsiElement && !((PsiElement)element).isValid()) {
          continue;
      }
        expandPathToElement(element);
    }
  }
  }

  public ActionGroup getGearActions() {
    DefaultActionGroup group = createActionGroup(true);
    group.addAction(new ToggleAction("Show Toolbar") {
      @Override
      public boolean isDumbAware() {
        return true;
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return getSettings().SHOW_TOOLBAR;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setToolbar(state ? createToolbar() : null);
        getSettings().SHOW_TOOLBAR = state;
      }
    }).setAsSecondary(true);
    return group;
  }

  private StructureViewFactoryImpl.State getSettings() {
    return ((StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject)).getState();
  }

  public AnAction[] getTitleActions() {
    return new AnAction[]{
      CommonActionsManager.getInstance().createExpandAllHeaderAction(getTree()),
      CommonActionsManager.getInstance().createCollapseAllHeaderAction(getTree()),
    };
  }

  protected ActionGroup createActionGroup() {
    return createActionGroup(false);
  }

  protected DefaultActionGroup createActionGroup(boolean togglesOnly) {
    DefaultActionGroup result = new DefaultActionGroup();
    Sorter[] sorters = myTreeModel.getSorters();
    for (final Sorter sorter : sorters) {
      if (sorter.isVisible()) {
        result.add(new TreeActionWrapper(sorter, this));
      }
    }
    if (sorters.length > 0) {
      result.addSeparator();
    }

    addGroupByActions(result);

    Filter[] filters = myTreeModel.getFilters();
    for (Filter filter : filters) {
      result.add(new TreeActionWrapper(filter, this));
    }
    if (myTreeModel instanceof ProvidingTreeModel) {
      final Collection<NodeProvider> providers = ((ProvidingTreeModel)myTreeModel).getNodeProviders();
      for (NodeProvider provider : providers) {
        result.add(new TreeActionWrapper(provider, this));
      }
    }

    if (!togglesOnly) {
      result.add(new ExpandAllAction(getTree()));
      result.add(new CollapseAllAction(getTree()));
    }

    if (showScrollToFromSourceActions()) {
      result.addSeparator();

      result.add(myAutoScrollToSourceHandler.createToggleAction());
      result.add(myAutoScrollFromSourceHandler.createToggleAction());
    }
    return result;
  }

  protected void addGroupByActions(DefaultActionGroup result) {
    Grouper[] groupers = myTreeModel.getGroupers();
    for (Grouper grouper : groupers) {
      result.add(new TreeActionWrapper(grouper, this));
    }
  }

  protected boolean showScrollToFromSourceActions() {
    return true;
  }

  @Override
  public FileEditor getFileEditor() {
    return myFileEditor;
  }

  public AsyncResult<AbstractTreeNode> expandPathToElement(Object element) {
    if (myAbstractTreeBuilder == null) return new AsyncResult.Rejected<AbstractTreeNode>();

    ArrayList<AbstractTreeNode> pathToElement = getPathToElement(element);
    if (pathToElement.isEmpty()) return new AsyncResult.Rejected<AbstractTreeNode>();

    final AsyncResult<AbstractTreeNode> result = new AsyncResult<AbstractTreeNode>();
    final AbstractTreeNode toExpand = pathToElement.get(pathToElement.size() - 1);
    myAbstractTreeBuilder.expand(toExpand, new Runnable() {
      @Override
      public void run() {
        result.setDone(toExpand);
      }
    });

    return result;
  }

  public boolean select(final Object element, final boolean requestFocus) {
    myAbstractTreeBuilder.getReady(this).doWhenDone(new Runnable() {
      @Override
      public void run() {
        expandPathToElement(element).doWhenDone(new Consumer<AbstractTreeNode>() {
          @Override
          public void consume(AbstractTreeNode abstractTreeNode) {
            myAbstractTreeBuilder.select(abstractTreeNode, new Runnable() {
              @Override
              public void run() {
                if (requestFocus) {
                  IdeFocusManager.getInstance(myProject).requestFocus(myAbstractTreeBuilder.getTree(), false);
                }
              }
            });
          }
        });
      }
    });
    return true;
  }

  private ArrayList<AbstractTreeNode> getPathToElement(Object element) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    final AbstractTreeStructure treeStructure = myAbstractTreeBuilder.getTreeStructure();
    if (treeStructure != null) {
      addToPath((AbstractTreeNode)treeStructure.getRootElement(), element, result, new THashSet<Object>());
    }
    return result;
  }

  private static boolean addToPath(AbstractTreeNode<?> rootElement, Object element, ArrayList<AbstractTreeNode> result, Collection<Object> processedElements) {
    Object value = rootElement.getValue();
    if (value instanceof StructureViewTreeElement) {
      value = ((StructureViewTreeElement) value).getValue();
    }
    if (!processedElements.add(value)){
        return false;
    }

    if (Comparing.equal(value, element)){
      result.add(0, rootElement);
      return true;
    }

    Collection<? extends AbstractTreeNode> children = rootElement.getChildren();
    for (AbstractTreeNode child : children) {
      if (addToPath(child, element, result, processedElements)) {
        result.add(0, rootElement);
        return true;
      }
    }

    return false;
  }

  private static DefaultMutableTreeNode findInChildren(DefaultMutableTreeNode currentTreeNode, AbstractTreeNode topPathElement) {
    for (int i = 0; i < currentTreeNode.getChildCount(); i++) {
      TreeNode child = currentTreeNode.getChildAt(i);
      if (((DefaultMutableTreeNode)child).getUserObject().equals(topPathElement))
      {
        return (DefaultMutableTreeNode)child;
      }
    }
    return null;
  }

  private void scrollToSelectedElement() {
    if (myAutoscrollFeedback) {
      myAutoscrollFeedback = false;
      return;
    }

    if (!getSettings().AUTOSCROLL_FROM_SOURCE) {
      return;
    }

    myAutoscrollAlarm.cancelAllRequests();
    myAutoscrollAlarm.addRequest(
        new Runnable() {
        @Override
        public void run() {
          if (myAbstractTreeBuilder == null) return;
          if (UIUtil.isFocusAncestor(StructureViewComponent.this)) return;
          scrollToSelectedElementInner();
        }
      }, 1000);
  }

  private void scrollToSelectedElementInner() {
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      final Object currentEditorElement = myTreeModel.getCurrentEditorElement();
      if (currentEditorElement != null) {
        select(currentEditorElement, false);
      }
    }
    catch (IndexNotReadyException ignore) {
    }
  }

  @Override
  public void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), Thread.currentThread().getName());
    myAbstractTreeBuilder = null;
    // this will also dispose wrapped TreeModel
    myTreeModelWrapper.dispose();
    myFileEditor = null;
  }

  public boolean isDisposed() {
    return myAbstractTreeBuilder == null;
  }

  @Override
  public void centerSelectedRow() {
    TreePath path = getTree().getSelectionPath();
    if (path == null) return;

    myAutoScrollToSourceHandler.setShouldAutoScroll(false);
    TreeUtil.showRowCentered(getTree(), getTree().getRowForPath(path), false);
    myAutoScrollToSourceHandler.setShouldAutoScroll(true);
  }

  @Override
  public void setActionActive(String name, boolean state) {
    StructureViewFactoryEx.getInstanceEx(myProject).setActiveAction(name, state);
    rebuild();
    TreeUtil.expand(getTree(), 2);
  }

  protected void rebuild() {
    storeState();
    ++ourSettingsModificationCount;
    ((SmartTreeStructure)myAbstractTreeBuilder.getTreeStructure()).rebuildTree();
    myAbstractTreeBuilder.updateFromRoot();
    restoreState();
  }

  @Override
  public boolean isActionActive(String name) {
    return !myProject.isDisposed() && StructureViewFactoryEx.getInstanceEx(myProject).isActionActive(name);
  }

  public AbstractTreeStructure getTreeStructure() {
    return myAbstractTreeBuilder.getTreeStructure();
  }

  public JTree getTree() {
    return myAbstractTreeBuilder.getTree();
  }

  public AbstractTreeBuilder getTreeBuilder() {
    return myAbstractTreeBuilder;
  }

  //public void setTreeBuilder(AbstractTreeBuilder treeBuilder) {
  //  myAbstractTreeBuilder = treeBuilder;
  //}

  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    private boolean myShouldAutoScroll = true;

    public void setShouldAutoScroll(boolean shouldAutoScroll) {
      myShouldAutoScroll = shouldAutoScroll;
    }

    @Override
    protected boolean isAutoScrollMode() {
      return myShouldAutoScroll && !myProject.isDisposed()
             && getSettings().AUTOSCROLL_MODE;
    }

    @Override
    protected void setAutoScrollMode(boolean state) {
      getSettings().AUTOSCROLL_MODE = state;
    }

    @Override
    protected void scrollToSource(Component tree) {
      if (myAbstractTreeBuilder == null) return;
      myAutoscrollFeedback = true;

      Navigatable editSourceDescriptor = CommonDataKeys.NAVIGATABLE.getData(DataManager.getInstance().getDataContext(getTree()));
      if (myFileEditor != null && editSourceDescriptor != null && editSourceDescriptor.canNavigateToSource()) {
        editSourceDescriptor.navigate(false);
      }
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private FileEditorPositionListener myFileEditorPositionListener;

    private MyAutoScrollFromSourceHandler(Project project, Disposable parentDisposable) {
      super(project, getTree(), parentDisposable);
    }

    @Override
    protected void selectElementFromEditor(@NotNull FileEditor editor) {
    }

    @Override
    public void install() {
      addEditorCaretListener();
    }

    @Override
    public void dispose() {
      myTreeModel.removeEditorPositionListener(myFileEditorPositionListener);
    }

    private void addEditorCaretListener() {
      myFileEditorPositionListener = new FileEditorPositionListener() {
        @Override
        public void onCurrentElementChanged() {
          scrollToSelectedElement();
        }
      };
      myTreeModel.addEditorPositionListener(myFileEditorPositionListener);
    }

    @Override
    protected boolean isAutoScrollEnabled() {
      return getSettings().AUTOSCROLL_FROM_SOURCE;
    }

    @Override
    protected void setAutoScrollEnabled(boolean state) {
      getSettings().AUTOSCROLL_FROM_SOURCE = state;
      final FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
      if (selectedEditors.length > 0 && state) {
        scrollToSelectedElementInner();
      }
    }
  }

  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      TreePath path = getSelectedUniquePath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object element = getNodeValue(node);
      if (element instanceof StructureViewTreeElement) {
        element = ((StructureViewTreeElement)element).getValue();
      }
      if (!(element instanceof PsiElement)) return null;
      if (!((PsiElement)element).isValid()) return null;
      return element;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      return convertToPsiElementsArray(getSelectedElements());
    }
    if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      return myFileEditor;
    }
    if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      Object[] selectedElements = getSelectedTreeElements();
      if (selectedElements == null || selectedElements.length == 0) return null;
      if (selectedElements[0] instanceof Navigatable) {
        return selectedElements[0];
      }
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return getHelpID();
    }
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    return super.getData(dataId);
  }

  @Nullable
  private static PsiElement[] convertToPsiElementsArray(final Object[] selectedElements) {
    if (selectedElements == null) return null;
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (Object selectedElement : selectedElements) {
      if (selectedElement instanceof PsiElement && ((PsiElement)selectedElement).isValid()) {
        psiElements.add((PsiElement)selectedElement);
      }
    }
    return PsiUtilCore.toPsiElementArray(psiElements);
  }

  @Nullable
  private TreePath getSelectedUniquePath() {
    JTree tree = getTree();
    if (tree == null) return null;
    TreePath[] paths = tree.getSelectionPaths();
    return paths == null || paths.length != 1 ? null : paths[0];
  }

  @Override
  @NotNull
  public StructureViewModel getTreeModel() {
    return myTreeModel;
  }

  @Override
  public boolean navigateToSelectedElement(boolean requestFocus) {
    return select(myTreeModel.getCurrentEditorElement(), requestFocus);
  }

  public void doUpdate() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myAbstractTreeBuilder.queueUpdate(true);
  }

//todo [kirillk] dirty hack for discovering invalid psi elements, to delegate it to a proper place after 8.1
  public static boolean isValid(Object treeElement) {
    if (treeElement instanceof StructureViewTreeElementWrapper) {
      final StructureViewTreeElementWrapper wrapper = (StructureViewTreeElementWrapper)treeElement;
      if (wrapper.getValue() instanceof PsiTreeElementBase) {
        final PsiTreeElementBase psiNode = (PsiTreeElementBase)wrapper.getValue();
        return psiNode.isValid();
      }
    }
    return true;
  }

  public static class StructureViewTreeElementWrapper extends TreeElementWrapper implements NodeDescriptorProvidingKey {
    private long childrenStamp = -1;
    private long modificationCountForChildren = ourSettingsModificationCount;

    public StructureViewTreeElementWrapper(Project project, TreeElement value, TreeModel treeModel) {
      super(project, value, treeModel);
    }

    @Override
    @NotNull
    public Object getKey() {
      StructureViewTreeElement element = (StructureViewTreeElement)getValue();
      if (element instanceof NodeDescriptorProvidingKey) return ((NodeDescriptorProvidingKey)element).getKey();
      Object value = element.getValue();
      return value == null ? this : value;
    }



    @Override
    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      if (ourSettingsModificationCount != modificationCountForChildren) {
        resetChildren();
        modificationCountForChildren = ourSettingsModificationCount;
      }

      final Object o = unwrapValue(getValue());
      long currentStamp;
      if (( o instanceof PsiElement &&
            ((PsiElement)o).getNode() instanceof CompositeElement &&
            childrenStamp != (currentStamp = ((CompositeElement)((PsiElement)o).getNode()).getModificationCount())
          ) ||
          ( o instanceof ModificationTracker &&
            childrenStamp != (currentStamp = ((ModificationTracker)o).getModificationCount())
          )
        ) {
        resetChildren();
        childrenStamp = currentStamp;
      }
      try {
        return super.getChildren();
      }
      catch (IndexNotReadyException ignore) {
        return Collections.emptyList();
      }
    }

    @Override
    public boolean isAlwaysShowPlus() {
      if (getElementInfoProvider() != null) {
        return getElementInfoProvider().isAlwaysShowsPlus((StructureViewTreeElement)getValue());
      }
      return true;
    }

    @Override
    public boolean isAlwaysLeaf() {
      if (getElementInfoProvider() != null) {
        return getElementInfoProvider().isAlwaysLeaf((StructureViewTreeElement)getValue());
      }

      return false;
    }

    @Nullable
    private StructureViewModel.ElementInfoProvider getElementInfoProvider() {
      if (myTreeModel instanceof StructureViewModel.ElementInfoProvider) {
        return ((StructureViewModel.ElementInfoProvider)myTreeModel);
      } else if (myTreeModel instanceof TreeModelWrapper) {
        StructureViewModel model = ((TreeModelWrapper)myTreeModel).getModel();
        if (model instanceof StructureViewModel.ElementInfoProvider) {
          return (StructureViewModel.ElementInfoProvider)model;
        }
      }

      return null;
    }

    @Override
    protected TreeElementWrapper createChildNode(final TreeElement child) {
      return new StructureViewTreeElementWrapper(myProject, child, myTreeModel);
    }

    @Override
    protected GroupWrapper createGroupWrapper(final Project project, Group group, final TreeModel treeModel) {
      return new StructureViewGroup(project, group, treeModel);
    }

    public boolean equals(Object o) {
      if (o instanceof StructureViewTreeElementWrapper) {
        return Comparing.equal(
          unwrapValue(getValue()),
          unwrapValue(((StructureViewTreeElementWrapper)o).getValue())
        );
      } else if (o instanceof StructureViewTreeElement) {
        return Comparing.equal(
          unwrapValue(getValue()),
          ((StructureViewTreeElement)o).getValue()
        );
      }
      return false;
    }

    private static Object unwrapValue(Object o) {
      if (o instanceof StructureViewTreeElement) {
        return ((StructureViewTreeElement)o).getValue();
      }
      else {
        return o;
      }
    }

    public int hashCode() {
      final Object o = unwrapValue(getValue());

      return o != null ? o.hashCode() : 0;
    }

    private class StructureViewGroup extends GroupWrapper {
      public StructureViewGroup(Project project, Group group, TreeModel treeModel) {
        super(project, group, treeModel);
      }

      @Override
      protected TreeElementWrapper createChildNode(TreeElement child) {
        return new StructureViewTreeElementWrapper(getProject(), child, myTreeModel);
      }


      @Override
      protected GroupWrapper createGroupWrapper(Project project, Group group, TreeModel treeModel) {
        return new StructureViewGroup(project, group, treeModel);
      }

      @Override
      public boolean isAlwaysShowPlus() {
        return true;
      }
    }
  }

  public String getHelpID() {
    return ourHelpID;
  }

  @Override
  public Dimension getCurrentSize() {
    return getTree().getSize();
  }

  @Override
  public void setReferenceSizeWhileInitializing(Dimension size) {
    _setRefSize(size);

    if (size != null) {
      myAbstractTreeBuilder.getReady(this).doWhenDone(new Runnable() {
        @Override
        public void run() {
          _setRefSize(null);
        }
      });
    }
  }

  private void _setRefSize(Dimension size) {
    JTree tree = getTree();
    tree.setPreferredSize(size);
    tree.setMinimumSize(size);
    tree.setMaximumSize(size);

    tree.revalidate();
    tree.repaint();
  }
}
