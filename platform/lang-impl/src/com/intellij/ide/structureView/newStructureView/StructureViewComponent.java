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

package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.impl.StructureViewState;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.ide.util.treeView.smartTree.TreeModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StructureViewComponent extends SimpleToolWindowPanel implements TreeActionsOwner, DataProvider, StructureView.Scrollable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.structureView.newStructureView.StructureViewComponent");
  @NonNls private static final String ourHelpID = "viewingStructure.fileStructureView";

  private StructureTreeBuilder myAbstractTreeBuilder;

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
  private Tree myTree;

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
      public void rebuildTree() {
        if (!isDisposed()) {
          super.rebuildTree();
        }
      }

      public boolean isToBuildChildrenInBackground(final Object element) {
        return getRootElement() == element;
      }

      protected TreeElementWrapper createTree() {
        return new StructureViewTreeElementWrapper(myProject, myModel.getRoot(), myModel);
      }
    };

    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(treeStructure.getRootElement()));
    myTree = new Tree(model);
    myTree.setRootVisible(showRootNode);
    myTree.setShowsRootHandles(true);

    myAbstractTreeBuilder = new StructureTreeBuilder(project, myTree,
                                                     (DefaultTreeModel)myTree.getModel(),treeStructure,myTreeModelWrapper) {
      @Override
      protected boolean validateNode(Object child) {
        return isValid(child);
      }
    };
    Disposer.register(this, myAbstractTreeBuilder);
    Disposer.register(myAbstractTreeBuilder, new Disposable() {
      public void dispose() {
        storeState();
      }
    });

    setContent(new JScrollPane(myAbstractTreeBuilder.getTree()));

    myAbstractTreeBuilder.getTree().setCellRenderer(new NodeRenderer());

    myAutoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler(myProject, this);

    JComponent toolbarComponent =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.STRUCTURE_VIEW_TOOLBAR, createActionGroup(), true).getComponent();
    setToolbar(toolbarComponent);

    installTree();

    myCopyPasteDelegator = new CopyPasteDelegator(myProject, getTree()) {
      @NotNull
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
  }

  private void installTree() {
    getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myAutoScrollToSourceHandler.install(getTree());
    myAutoScrollFromSourceHandler.install();

    TreeUtil.installActions(getTree());
    new TreeSpeedSearch(getTree());

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
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private Object[] getSelectedElements() {
    final JTree tree = getTree();
    return tree != null ? convertPathsToValues(tree.getSelectionPaths()): ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] getSelectedTreeElements() {
    return convertPathsToTreeElements(getTree().getSelectionPaths());
  }


  private static Object[] convertPathsToValues(TreePath[] selectionPaths) {
    if (selectionPaths != null) {
      List<Object> result = new ArrayList<Object>();

      for (TreePath selectionPath : selectionPaths) {
        final Object userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
        if (userObject instanceof AbstractTreeNode) {
          Object value = ((AbstractTreeNode)userObject).getValue();
          if (value instanceof StructureViewTreeElement) {
            value = ((StructureViewTreeElement)value).getValue();
          }
          result.add(value);
        }
      }
      return ArrayUtil.toObjectArray(result);
    }
    else {
      return null;
    }
  }

  private static Object[] convertPathsToTreeElements(TreePath[] selectionPaths) {
    if (selectionPaths != null) {
      Object[] result = new Object[selectionPaths.length];

      for (int i = 0; i < selectionPaths.length; i++) {
        Object userObject = ((DefaultMutableTreeNode)selectionPaths[i].getLastPathComponent()).getUserObject();
        if (!(userObject instanceof AbstractTreeNode)) return null;
        result[i] = ((AbstractTreeNode)userObject).getValue();
      }
      return result;
    }
    else {
      return null;
    }
  }

  private void addTreeMouseListeners() {
    EditSourceOnDoubleClickHandler.install(getTree());
    CustomizationUtil.installPopupHandler(getTree(), IdeActions.GROUP_STRUCTURE_VIEW_POPUP, ActionPlaces.STRUCTURE_VIEW_POPUP);
  }

  private void addTreeKeyListener() {
    getTree().addKeyListener(
        new KeyAdapter() {
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

  protected ActionGroup createActionGroup() {
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

    Grouper[] groupers = myTreeModel.getGroupers();
    for (Grouper grouper : groupers) {
      result.add(new TreeActionWrapper(grouper, this));
    }
    Filter[] filters = myTreeModel.getFilters();
    for (Filter filter : filters) {
      result.add(new TreeActionWrapper(filter, this));
    }

    if (showScrollToFromSourceActions()) {
      result.addSeparator();

      result.add(myAutoScrollToSourceHandler.createToggleAction());
      result.add(myAutoScrollFromSourceHandler.createToggleAction());
    }
    result.addSeparator();
    result.add(new ContextHelpAction(getHelpID()));
    return result;
  }

  protected boolean showScrollToFromSourceActions() {
    return true;
  }

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
      public void run() {
        result.setDone(toExpand);
      }
    });

    return result;
  }

  public boolean select(final Object element, final boolean requestFocus) {
    myAbstractTreeBuilder.getReady(this).doWhenDone(new Runnable() {
      public void run() {
        expandPathToElement(element).doWhenDone(new AsyncResult.Handler<AbstractTreeNode>() {
          public void run(AbstractTreeNode abstractTreeNode) {
            myAbstractTreeBuilder.select(abstractTreeNode, new Runnable() {
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
    addToPath((AbstractTreeNode)myAbstractTreeBuilder.getTreeStructure().getRootElement(), element, result, new THashSet<Object>());
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

    StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject);

    if (!structureViewFactory.getState().AUTOSCROLL_FROM_SOURCE) {
      return;
    }

    myAutoscrollAlarm.cancelAllRequests();
    myAutoscrollAlarm.addRequest(
        new Runnable() {
        public void run() {
          if (myAbstractTreeBuilder == null) {
            return;
          }
          selectViewableElement();
        }
      }, 1000
    );
  }

  private void selectViewableElement() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final Object currentEditorElement = myTreeModel.getCurrentEditorElement();
    if (currentEditorElement != null) {
      select(currentEditorElement, false);
    }
  }


  public JComponent getComponent() {
    return this;
  }

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

  public void centerSelectedRow() {
    TreePath path = getTree().getSelectionPath();
    if (path == null)
    {
      return;
    }
    myAutoScrollToSourceHandler.setShouldAutoScroll(false);
    TreeUtil.showRowCentered(getTree(), getTree().getRowForPath(path), false);
    myAutoScrollToSourceHandler.setShouldAutoScroll(true);
  }

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

  public boolean isActionActive(String name) {
    return !myProject.isDisposed() && StructureViewFactoryEx.getInstanceEx(myProject).isActionActive(name);
  }

  public AbstractTreeStructure getTreeStructure() {
    return myAbstractTreeBuilder.getTreeStructure();
  }

  public JTree getTree() {
    return myTree;
  }

  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    private boolean myShouldAutoScroll = true;

    public void setShouldAutoScroll(boolean shouldAutoScroll) {
      myShouldAutoScroll = shouldAutoScroll;
    }

    protected boolean isAutoScrollMode() {
      return myShouldAutoScroll && ((StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject)).getState().AUTOSCROLL_MODE;
    }

    protected void setAutoScrollMode(boolean state) {
      ((StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject)).getState().AUTOSCROLL_MODE = state;
    }

    protected void scrollToSource(Component tree) {
      if (myAbstractTreeBuilder == null) return;
      myAutoscrollFeedback = true;

      Navigatable editSourceDescriptor = LangDataKeys.NAVIGATABLE.getData(DataManager.getInstance().getDataContext(getTree()));
      if (myFileEditor != null && editSourceDescriptor != null && editSourceDescriptor.canNavigateToSource()) {
        editSourceDescriptor.navigate(false);
      }
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private FileEditorPositionListener myFileEditorPositionListener;

    private MyAutoScrollFromSourceHandler(Project project, Disposable parentDisposable) {
      super(project, parentDisposable);
    }

    public void install() {
      addEditorCaretListener();
    }

    public void dispose() {
      myTreeModel.removeEditorPositionListener(myFileEditorPositionListener);
    }

    private void addEditorCaretListener() {
      myFileEditorPositionListener = new FileEditorPositionListener() {
        public void onCurrentElementChanged() {
          scrollToSelectedElement();
        }
      };
      myTreeModel.addEditorPositionListener(myFileEditorPositionListener);
    }

    protected boolean isAutoScrollMode() {
      StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject);
      return structureViewFactory.getState().AUTOSCROLL_FROM_SOURCE;
    }

    protected void setAutoScrollMode(boolean state) {
      StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject);
      structureViewFactory.getState().AUTOSCROLL_FROM_SOURCE = state;
      final FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
      if (selectedEditors.length > 0 && state) {
        scrollToSelectedElement();
      }
    }
  }

  public Object getData(String dataId) {
    if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      TreePath path = getSelectedUniquePath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof AbstractTreeNode)) return null;
      AbstractTreeNode descriptor = (AbstractTreeNode)userObject;
      Object element = descriptor.getValue();
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
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      Object[] selectedElements = getSelectedTreeElements();
      if (selectedElements == null || selectedElements.length == 0) return null;
      if (selectedElements[0] instanceof Navigatable) {
        return selectedElements[0];
      }
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return getHelpID();
    }
    return super.getData(dataId);
  }

  private static PsiElement[] convertToPsiElementsArray(final Object[] selectedElements) {
    if (selectedElements == null) return null;
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (Object selectedElement : selectedElements) {
      if (selectedElement instanceof PsiElement && ((PsiElement)selectedElement).isValid()) {
        psiElements.add((PsiElement)selectedElement);
      }
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private TreePath getSelectedUniquePath() {
    JTree tree = getTree();
    if (tree == null) return null;
    TreePath[] paths = tree.getSelectionPaths();
    return paths == null || paths.length != 1 ? null : paths[0];
  }

  public StructureViewModel getTreeModel() {
    return myTreeModel;
  }

  public boolean navigateToSelectedElement(boolean requestFocus) {
    return select(myTreeModel.getCurrentEditorElement(), requestFocus);
  }
  
  public void doUpdate() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ((StructureTreeBuilder)myAbstractTreeBuilder).addRootToUpdate();
  }

//todo [kirillk] dirty hack for discovering invalid psi elements, to delegate it to a proper place after 8.1
  private static boolean isValid(Object treeElement) {
    if (treeElement instanceof StructureViewTreeElementWrapper) {
      final StructureViewTreeElementWrapper wrapper = (StructureViewTreeElementWrapper)treeElement;
      if (wrapper.getValue() instanceof PsiTreeElementBase) {
        final PsiTreeElementBase psiNode = (PsiTreeElementBase)wrapper.getValue();
        return psiNode.isValid();
      }
    }
    return true;
  }

  static class StructureViewTreeElementWrapper extends TreeElementWrapper implements NodeDescriptorProvidingKey {
    private long childrenStamp = -1;
    private long modificationCountForChildren = ourSettingsModificationCount;

    public StructureViewTreeElementWrapper(Project project, TreeElement value, TreeModel treeModel) {
      super(project, value, treeModel);
    }

    @NotNull
    public Object getKey() {
      StructureViewTreeElement element = (StructureViewTreeElement)getValue();
      if (element instanceof NodeDescriptorProvidingKey) return ((NodeDescriptorProvidingKey)element).getKey();
      Object value = element.getValue();
      return value == null ? this : value;
    }



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
      return super.getChildren();
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

  public Dimension getCurrentSize() {
    return myTree.getSize();
  }

  public void setReferenceSizeWhileInitializing(Dimension size) {
    _setRefSize(size);

    if (size != null) {
      myAbstractTreeBuilder.getReady(this).doWhenDone(new Runnable() {
        public void run() {
          _setRefSize(null);
        }
      });
    }
  }

  private void _setRefSize(Dimension size) {
    myTree.setPreferredSize(size);
    myTree.setMinimumSize(size);
    myTree.setMaximumSize(size);

    myTree.revalidate();
    myTree.repaint();
  }
}
