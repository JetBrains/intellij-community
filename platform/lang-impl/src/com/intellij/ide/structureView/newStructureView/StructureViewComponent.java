/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.customRegions.CustomRegionTreeElement;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.*;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.*;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StructureViewComponent extends SimpleToolWindowPanel implements TreeActionsOwner, DataProvider, StructureView.Scrollable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.structureView.newStructureView.StructureViewComponent");

  private static final Key<TreeState> STRUCTURE_VIEW_STATE_KEY = Key.create("STRUCTURE_VIEW_STATE");
  private static AtomicInteger ourSettingsModificationCount = new AtomicInteger();
  private final boolean myUseATM = true; //todo inline & remove

  private FileEditor myFileEditor;
  private final TreeModelWrapper myTreeModelWrapper;

  private final Project myProject;
  private final StructureViewModel myTreeModel;

  private final Tree myTree;
  private final SmartTreeStructure myTreeStructure;
  private final StructureTreeBuilder myTreeBuilder;

  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;
  private final SingleAlarm myUpdateAlarm;

  private volatile AsyncPromise<TreePath> myCurrentFocusPromise;

  private TreeState myStructureViewState;
  private boolean myAutoscrollFeedback;
  private boolean myDisposed;

  private final Alarm myAutoscrollAlarm = new Alarm(this);

  private final CopyPasteDelegator myCopyPasteDelegator;
  private final MyAutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final AutoScrollFromSourceHandler myAutoScrollFromSourceHandler;


  public StructureViewComponent(@Nullable FileEditor editor,
                                @NotNull StructureViewModel structureViewModel,
                                @NotNull Project project,
                                boolean showRootNode) {
    super(true, true);

    myProject = project;
    myFileEditor = editor;
    myTreeModel = structureViewModel;
    myTreeModelWrapper = new TreeModelWrapper(myTreeModel, this);

    myTreeStructure = new SmartTreeStructure(project, myTreeModelWrapper) {
      @Override
      public void rebuildTree() {
        if (isDisposed()) return;
        super.rebuildTree();
      }

      @Override
      public boolean isToBuildChildrenInBackground(final Object element) {
        return Registry.is("ide.structureView.StructureViewTreeStructure.BuildChildrenInBackground") ||
               getRootElement() == element;
      }

      @Override
      protected TreeElementWrapper createTree() {
        return new MyNodeWrapper(myProject, myModel.getRoot(), myModel);
      }

      @Override
      public String toString() {
        return "structure view tree structure(model=" + myTreeModel + ")";
      }
    };

    if (myUseATM) {
      myStructureTreeModel = new StructureTreeModel(true);
      myStructureTreeModel.setStructure(myTreeStructure);
      myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, true);
      myAsyncTreeModel.setRootImmediately(myStructureTreeModel.getRootImmediately());
      myTree = new MyTree(myAsyncTreeModel);

      Disposer.register(this, () -> myTreeModelWrapper.dispose());
      Disposer.register(this, myAsyncTreeModel);

      registerAutoExpandListener(myTree, myTreeModel);

      myUpdateAlarm = new SingleAlarm(this::rebuild, 200, this);
      myTreeBuilder = null;
    }
    else {
      myStructureTreeModel = null;
      myAsyncTreeModel = null;
      myUpdateAlarm = null;
      myTree = new MyTree(new DefaultTreeModel(new DefaultMutableTreeNode(myTreeStructure.getRootElement())));
      myTreeBuilder = new StructureTreeBuilder(project, myTree, (DefaultTreeModel)myTree.getModel(),
                                               myTreeStructure, myTreeModelWrapper) {
        @Override
        protected boolean validateNode(Object child) {
          return !(child instanceof ValidateableNode) || ((ValidateableNode)child).isValid();
        }
      };
      Disposer.register(this, myTreeBuilder);
    }
    myTree.setRootVisible(showRootNode);
    myTree.getEmptyText().setText("Structure is empty");

    final ModelListener modelListener = () -> queueUpdate();
    myTreeModelWrapper.addModelListener(modelListener);

    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        storeState();
        myTreeModelWrapper.removeModelListener(modelListener);
      }
    });

    setContent(ScrollPaneFactory.createScrollPane(myTree));

    myAutoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler(myProject, this);
    myCopyPasteDelegator = createCopyPasteDelegator(myProject, myTree);

    setToolbar(createToolbar());
    setupTree();
  }

  public static void registerAutoExpandListener(@NotNull JTree tree, @NotNull StructureViewModel structureViewModel) {
    tree.getModel().addTreeModelListener(new MyExpandListener(
      tree, ObjectUtils.tryCast(structureViewModel, StructureViewModel.ExpandInfoProvider.class)));
  }

  @NotNull
  public static CopyPasteDelegator createCopyPasteDelegator(@NotNull Project project, @NotNull JTree tree) {
    return new CopyPasteDelegator(project, tree) {
      @Override
      @NotNull
      protected PsiElement[] getSelectedElements() {
        return PsiUtilCore.toPsiElementArray(getSelectedValues(tree).filter(PsiElement.class).toList());
      }
    };
  }

  protected boolean showScrollToFromSourceActions() {
    return true;
  }

  @Override
  public FileEditor getFileEditor() {
    return myFileEditor;
  }

  private StructureViewFactoryImpl.State getSettings() {
    return ((StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject)).getState();
  }

  public void showToolbar() {
    setToolbar(createToolbar());
  }

  private JComponent createToolbar() {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.STRUCTURE_VIEW_TOOLBAR, createActionGroup(), true).getComponent();
  }

  private void setupTree() {
    myTree.setCellRenderer(new NodeRenderer());
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.setShowsRootHandles(true);
    registerPsiListener(myProject, this, this::queueUpdate);

    myAutoScrollToSourceHandler.install(myTree);
    myAutoScrollFromSourceHandler.install();

    TreeUtil.installActions(getTree());

    new TreeSpeedSearch(getTree(), treePath -> {
      Object userObject = TreeUtil.getUserObject(treePath.getLastPathComponent());
      return userObject != null ? FileStructurePopup.getSpeedSearchText(userObject) : null;
    });

    addTreeKeyListener();
    addTreeMouseListeners();
    restoreState();
  }

  public static void registerPsiListener(@NotNull Project project, @NotNull Disposable disposable, @NotNull Runnable onChange) {
    MyPsiTreeChangeListener psiListener = new MyPsiTreeChangeListener(
      PsiManager.getInstance(project).getModificationTracker(), onChange);
    PsiManager.getInstance(project).addPsiTreeChangeListener(psiListener, disposable);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public JTree getTree() {
    return myTree;
  }

  public void queueUpdate() {
    if (myUseATM) {
      myUpdateAlarm.cancelAndRequest();
    }
    else {
      myTreeBuilder.queueUpdate();
    }
  }

  public void rebuild() {
    if (myUseATM) {
      myStructureTreeModel.getInvoker().invokeLaterIfNeeded(() -> {
        myTreeStructure.rebuildTree();
        myStructureTreeModel.invalidate(null);
      });
    }
    else {
      myTreeBuilder.queueUpdate();
    }
  }

  @NotNull
  private static JBTreeTraverser<Object> traverser() {
    return JBTreeTraverser.from(o -> o instanceof Group ? ((Group)o).getChildren() : null);
  }

  private JBIterable<Object> getSelectedValues() {
    return getSelectedValues(getTree());
  }

  @NotNull
  public static JBIterable<Object> getSelectedValues(JTree tree) {
    return traverser()
      .withRoots(JBIterable.of(tree.getSelectionPaths())
                   .map(TreePath::getLastPathComponent)
                   .filterMap(StructureViewComponent::unwrapValue))
      .traverse();
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
            if (e.isConsumed()) return;
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
    if (isDisposed()) return;
    Object root = myTree.getModel().getRoot();
    if (root == null) return;
    myStructureViewState = TreeState.createOn(myTree, new TreePath(root));
    if (myFileEditor != null) {
      myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, myStructureViewState);
    }
  }

  @Override
  public void restoreState() {
    myStructureViewState = myFileEditor == null ? null : myFileEditor.getUserData(STRUCTURE_VIEW_STATE_KEY);
    if (myStructureViewState == null) {
      TreeUtil.expand(getTree(), 2);
    }
    else {
      myStructureViewState.applyTo(myTree);
      myStructureViewState = null;
      if (myFileEditor != null) {
        myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, null);
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

    result.add(new ExpandAllAction(getTree()));
    result.add(new CollapseAllAction(getTree()));

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

  public AsyncResult<AbstractTreeNode> expandPathToElement(Object element) {
    AsyncResult<AbstractTreeNode> result = new AsyncResult<>();
    expandSelectFocusInner(element, false, false).processed(p -> {
      if (p == null) result.setRejected();
      else result.setDone(ObjectUtils.tryCast(TreeUtil.getUserObject(p.getLastPathComponent()), AbstractTreeNode.class));
    });
    return result;
  }

  @NotNull
  public Promise<TreePath> select(Object element, boolean requestFocus) {
    return expandSelectFocusInner(element, true, requestFocus);
  }

  @NotNull
  private Promise<TreePath> expandSelectFocusInner(Object element, boolean select, boolean requestFocus) {
    AsyncPromise<TreePath> result = myCurrentFocusPromise = new AsyncPromise<>();
    if (!myUseATM) {
      ArrayList<AbstractTreeNode> pathToElement = getPathToElement(element);
      if (pathToElement.isEmpty()) return Promises.rejectedPromise();
      TreePath path = new TreePath(pathToElement.toArray());
      myTreeBuilder.expand(path.getLastPathComponent(), () -> {
        if (myCurrentFocusPromise != result) {
          result.setError("rejected");
        }
        else {
          if (select) myTreeBuilder.select(path.getLastPathComponent());
          if (requestFocus) {
            IdeFocusManager.getInstance(myProject).requestFocus(myTree, false);
          }
          result.setResult(path);
        }
      });
      return result;
    }
    int[] stage = { 1, 0 }; // 1 - first pass, 2 - optimization applied, 3 - retry w/o optimization
    TreePath[] deepestPath = { null };
    TreeVisitor visitor = path -> {
      if (myCurrentFocusPromise != result) {
        result.setError("rejected");
        return TreeVisitor.Action.INTERRUPT;
      }
      Object last = path.getLastPathComponent();
      Object userObject = unwrapNavigatable(last);
      Object value = unwrapValue(last);
      if (Comparing.equal(value, element) ||
          userObject instanceof AbstractTreeNode && ((AbstractTreeNode)userObject).canRepresent(element)) {
        return TreeVisitor.Action.INTERRUPT;
      }
      if (value instanceof PsiElement && element instanceof PsiElement) {
        if (PsiTreeUtil.isAncestor((PsiElement)value, (PsiElement)element, true)) {
          int count = path.getPathCount();
          if (stage[1] == 0 || stage[1] < count) {
            stage[1] = count;
            deepestPath[0] = path;
          }
        }
        else if (stage[0] != 3) {
          stage[0] = 2;
          return TreeVisitor.Action.SKIP_CHILDREN;
        }
      }
      return TreeVisitor.Action.CONTINUE;
    };
    Function<TreePath, Promise<TreePath>> action = path -> {
      if (select) TreeUtil.selectPath(myTree, path);
      else myTree.expandPath(path);
      if (requestFocus) {
        IdeFocusManager.getInstance(myProject).requestFocus(myTree, false);
      }
      return Promises.resolvedPromise(path);
    };
    Function<TreePath, Promise<TreePath>> fallback = new Function<TreePath, Promise<TreePath>>() {
      @Override
      public Promise<TreePath> fun(TreePath path) {
        if (myCurrentFocusPromise != result) {
          result.setError("rejected");
          return Promises.rejectedPromise();
        }
        else if (path == null && stage[0] == 2) {
          // Some structure views merge unrelated psi elements into a structure node (MarkdownStructureViewModel).
          // So turn off the isAncestor() optimization and retry once.
          stage[0] = 3;
          return myAsyncTreeModel.accept(visitor).thenAsync(this);
        }
        else {
          TreePath adjusted = path == null ? deepestPath[0] : path;
          return adjusted == null ? Promises.rejectedPromise() : action.fun(adjusted);
        }
      }
    };
    myAsyncTreeModel.accept(visitor).thenAsync(fallback).processed(result);
    return myCurrentFocusPromise;
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
      () -> {
        if (isDisposed()) return;
        if (UIUtil.isFocusAncestor(this)) return;
        scrollToSelectedElementInner();
      }, 1000);
  }

  private void scrollToSelectedElementInner() {
    PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(() -> {
      try {
        final Object currentEditorElement = myTreeModel.getCurrentEditorElement();
        if (currentEditorElement != null) {
          select(currentEditorElement, false);
        }
      }
      catch (IndexNotReadyException ignore) {
      }
    });
  }

  @Override
  public void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), Thread.currentThread().getName());
    myDisposed = true;
    myFileEditor = null;
  }

  public boolean isDisposed() {
    return myDisposed;
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    storeState();
    StructureViewFactoryEx.getInstanceEx(myProject).setActiveAction(name, state);
    ourSettingsModificationCount.incrementAndGet();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      rebuild();
    }
    else {
      AtomicBoolean complete = new AtomicBoolean(false);
      //noinspection TestOnlyProblems
      Promise<Void> promise = rebuildAndUpdate().processed(ignore -> complete.set(true));
      while (!complete.get()) {
        //noinspection TestOnlyProblems
        UIUtil.dispatchAllInvocationEvents();
        try {
          promise.blockingGet(20, TimeUnit.MILLISECONDS);
        }
        catch (Exception ignore) {
        }
      }
      //noinspection TestOnlyProblems
      UIUtil.dispatchAllInvocationEvents();
    }
    TreeUtil.expand(getTree(), 2);
  }

  @Override
  public boolean isActionActive(String name) {
    return !myProject.isDisposed() && StructureViewFactoryEx.getInstanceEx(myProject).isActionActive(name);
  }

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
      if (isDisposed()) return;
      myAutoscrollFeedback = true;

      Navigatable navigatable = CommonDataKeys.NAVIGATABLE.getData(DataManager.getInstance().getDataContext(getTree()));
      if (myFileEditor != null && navigatable != null && navigatable.canNavigateToSource()) {
        navigatable.navigate(false);
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
      super.dispose();
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

      if (isAutoScrollEnabled()) {
        //otherwise on any tab switching selection will be staying at the top file node until we made a first caret move
        scrollToSelectedElement();
      }
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
      PsiElement element = getSelectedValues().filter(PsiElement.class).single();
      return element != null && element.isValid() ? element : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      return PsiUtilCore.toPsiElementArray(getSelectedValues().filter(PsiElement.class).toList());
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
      List<Object> list = JBIterable.of(getTree().getSelectionPaths())
        .map(TreePath::getLastPathComponent)
        .map(StructureViewComponent::unwrapNavigatable)
        .toList();
      Object[] selectedElements = list.isEmpty() ? null : ArrayUtil.toObjectArray(list);
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

  @Override
  @NotNull
  public StructureViewModel getTreeModel() {
    return myTreeModel;
  }

  @Override
  public boolean navigateToSelectedElement(boolean requestFocus) {
    select(myTreeModel.getCurrentEditorElement(), requestFocus);
    return true;
  }

  @TestOnly
  public AsyncPromise<Void> rebuildAndUpdate() {
    AsyncPromise<Void> result = new AsyncPromise<>();
    if (!myUseATM) {
      myTreeBuilder.queueUpdate().doWhenDone(() -> result.setResult(null)).doWhenRejected(() -> result.setError("rejected"));
      return result;
    }
    rebuild();
    TreeVisitor visitor = path -> {
      Object o = TreeUtil.getUserObject(path.getLastPathComponent());
      if (o instanceof AbstractTreeNode) ((AbstractTreeNode)o).update();
      return TreeVisitor.Action.CONTINUE;
    };
    myAsyncTreeModel.accept(visitor).processed(ignore -> result.setResult(null));
    return result;
  }

  public String getHelpID() {
    return HelpID.STRUCTURE_VIEW;
  }

  @Override
  public Dimension getCurrentSize() {
    return getTree().getSize();
  }

  @Override
  public void setReferenceSizeWhileInitializing(Dimension size) {
    //_setRefSize(size);
    //
    //if (size != null) {
    //  todo com.intellij.ui.tree.AsyncTreeModelTest.invokeWhenProcessingDone() //
    //  myAbstractTreeBuilder.getReady(this).doWhenDone(() -> _setRefSize(null));
    //}
  }

  //private void _setRefSize(Dimension size) {
  //  JTree tree = getTree();
  //  tree.setPreferredSize(size);
  //  tree.setMinimumSize(size);
  //  tree.setMaximumSize(size);
  //
  //  tree.revalidate();
  //  tree.repaint();
  //}

  private static class MyNodeWrapper extends TreeElementWrapper
    implements NodeDescriptorProvidingKey, ValidateableNode {

    private long childrenStamp = -1;
    private int modificationCountForChildren = ourSettingsModificationCount.get();

    MyNodeWrapper(Project project, TreeElement value, TreeModel treeModel) {
      super(project, value, treeModel);
    }

    @Override
    @NotNull
    public Object getKey() {
      StructureViewTreeElement element = (StructureViewTreeElement)getValue();
      if (element instanceof NodeDescriptorProvidingKey) return ((NodeDescriptorProvidingKey)element).getKey();
      Object value = element == null ? null : element.getValue();
      return value == null ? this : value;
    }

    @Override
    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      if (ourSettingsModificationCount.get() != modificationCountForChildren) {
        resetChildren();
        modificationCountForChildren = ourSettingsModificationCount.get();
      }

      Object o = unwrapElement(getValue());
      long currentStamp = -1;
      if (o instanceof PsiElement) {
        if (!((PsiElement)o).isValid()) return Collections.emptyList();

        PsiFile file = ((PsiElement)o).getContainingFile();
        if (file != null) {
          currentStamp = file.getModificationStamp();
        }
      }
      else if (o instanceof ModificationTracker) {
        currentStamp = ((ModificationTracker)o).getModificationCount();
      }
      if (childrenStamp != currentStamp) {
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
      StructureViewModel.ElementInfoProvider elementInfoProvider = getElementInfoProvider();
      return elementInfoProvider == null || elementInfoProvider.isAlwaysShowsPlus((StructureViewTreeElement)getValue());
    }

    @Override
    public boolean isAlwaysLeaf() {
      StructureViewModel.ElementInfoProvider elementInfoProvider = getElementInfoProvider();
      return elementInfoProvider != null && elementInfoProvider.isAlwaysLeaf((StructureViewTreeElement)getValue());
    }

    @Nullable
    private StructureViewModel.ElementInfoProvider getElementInfoProvider() {
      if (myTreeModel instanceof StructureViewModel.ElementInfoProvider) {
        return (StructureViewModel.ElementInfoProvider)myTreeModel;
      }
      if (myTreeModel instanceof TreeModelWrapper) {
        StructureViewModel model = ((TreeModelWrapper)myTreeModel).getModel();
        if (model instanceof StructureViewModel.ElementInfoProvider) {
          return (StructureViewModel.ElementInfoProvider)model;
        }
      }

      return null;
    }

    @Override
    protected TreeElementWrapper createChildNode(@NotNull TreeElement child) {
      return new MyNodeWrapper(myProject, child, myTreeModel);
    }

    @Override
    protected GroupWrapper createGroupWrapper(Project project, @NotNull Group group, final TreeModel treeModel) {
      return new MyGroupWrapper(project, group, treeModel);
    }

    public boolean equals(Object o) {
      if (o instanceof MyNodeWrapper) {
        return Comparing.equal(unwrapElement(getValue()), unwrapElement(((MyNodeWrapper)o).getValue()));
      }
      else if (o instanceof StructureViewTreeElement) {
        return Comparing.equal(unwrapElement(getValue()), ((StructureViewTreeElement)o).getValue());
      }
      return false;
    }

    @Override
    public boolean isValid() {
      TreeElement value = getValue();
      PsiTreeElementBase psi = value instanceof PsiTreeElementBase ? (PsiTreeElementBase)value : null;
      return psi == null || psi.isValid();
    }

    public int hashCode() {
      final Object o = unwrapElement(getValue());

      return o != null ? o.hashCode() : 0;
    }
  }

  private static class MyGroupWrapper extends GroupWrapper {
    MyGroupWrapper(Project project, Group group, TreeModel treeModel) {
      super(project, group, treeModel);
    }

    @Override
    protected TreeElementWrapper createChildNode(@NotNull TreeElement child) {
      return new MyNodeWrapper(getProject(), child, myTreeModel);
    }


    @Override
    protected GroupWrapper createGroupWrapper(Project project, @NotNull Group group, TreeModel treeModel) {
      return new MyGroupWrapper(project, group, treeModel);
    }

    @Override
    public boolean isAlwaysShowPlus() {
      return true;
    }
  }

  private static class MyTree extends DnDAwareTree implements PlaceProvider<String> {
    MyTree(javax.swing.tree.TreeModel model) {
      super(model);
      HintUpdateSupply.installDataContextHintUpdateSupply(this);
    }

    @Override
    public String getPlace() {
      return ActionPlaces.STRUCTURE_VIEW_TOOLBAR;
    }
  }

  private static class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    final PsiModificationTracker modTracker;
    long prevModCount;
    final Runnable onChange;

    private MyPsiTreeChangeListener(PsiModificationTracker modTracker, Runnable onChange) {
      this.modTracker = modTracker;
      this.onChange = onChange;
      prevModCount = modTracker.getOutOfCodeBlockModificationCount();
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getOldChild();
      if (child instanceof PsiWhiteSpace) return; //optimization

      childrenChanged();
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getNewChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      PsiElement oldChild = event.getOldChild();
      PsiElement newChild = event.getNewChild();
      if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      childrenChanged();
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      childrenChanged();
    }

    private void childrenChanged() {
      long newModificationCount = modTracker.getModificationCount();
      if (newModificationCount == prevModCount) return;
      prevModCount = newModificationCount;
      onChange.run();
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      childrenChanged();
    }
  }

  public static Object unwrapValue(Object o) {
    return unwrapElement(unwrapWrapper(o));
  }

  @Nullable
  public static Object unwrapNavigatable(Object o) {
    Object p = TreeUtil.getUserObject(o);
    return p instanceof FilteringTreeStructure.FilteringNode ? ((FilteringTreeStructure.FilteringNode)p).getDelegate() : p;
  }

  public static Object unwrapWrapper(Object o) {
    Object p = unwrapNavigatable(o);
    return p instanceof MyNodeWrapper ? ((MyNodeWrapper)p).getValue() :
           p instanceof MyGroupWrapper ? ((MyGroupWrapper)p).getValue() : p;
  }

  private static Object unwrapElement(Object o) {
    return o instanceof StructureViewTreeElement ? ((StructureViewTreeElement)o).getValue() : o;
  }

  // for FileStructurePopup only
  public static TreeElementWrapper createWrapper(Project project, TreeElement value, TreeModel treeModel) {
    return new MyNodeWrapper(project, value, treeModel);
  }

  private static class MyExpandListener extends TreeModelAdapter {
    private final JTree tree;
    final StructureViewModel.ExpandInfoProvider provider;
    final boolean smartExpand;

    MyExpandListener(@NotNull JTree tree, @Nullable StructureViewModel.ExpandInfoProvider provider) {
      this.tree = tree;
      this.provider = provider;
      smartExpand = provider != null && provider.isSmartExpand();
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      Object[] children = e.getChildren();
      if (smartExpand && children.length == 1) {
        ApplicationManager.getApplication().invokeLater(
          () -> tree.expandPath(e.getTreePath().pathByAddingChild(children[0])));
      }
      else {
        for (Object o : children) {
          Object userObject = TreeUtil.getUserObject(o);
          if (userObject instanceof NodeDescriptor && isAutoExpandNode((NodeDescriptor)userObject)) {
            ApplicationManager.getApplication().invokeLater(
              () -> tree.expandPath(e.getTreePath().pathByAddingChild(o)));
          }
        }
      }
    }

    boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
      if (provider != null) {
        Object value = unwrapWrapper(nodeDescriptor.getElement());
        if (value instanceof CustomRegionTreeElement) {
          return true;
        }
        else if (value instanceof StructureViewTreeElement) {
          return provider.isAutoExpand((StructureViewTreeElement)value);
        }
        else if (value instanceof GroupWrapper) {
          Group group = ObjectUtils.notNull(((GroupWrapper)value).getValue());
          for (TreeElement treeElement : group.getChildren()) {
            if (treeElement instanceof StructureViewTreeElement && !provider.isAutoExpand((StructureViewTreeElement)treeElement)) {
              return false;
            }
          }
        }
      }
      // expand root node & its immediate children
      NodeDescriptor parent = nodeDescriptor.getParentDescriptor();
      return parent == null || parent.getParentDescriptor() == null;
    }
  }

  // todo remove ASAP ------------------------------------

  @Deprecated
  @Nullable
  public AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  private ArrayList<AbstractTreeNode> getPathToElement(Object element) {
    ArrayList<AbstractTreeNode> result = new ArrayList<>();
    final AbstractTreeStructure treeStructure = myTreeBuilder.getTreeStructure();
    if (treeStructure != null) {
      addToPath((AbstractTreeNode)treeStructure.getRootElement(), element, result, new THashSet<>());
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
}
