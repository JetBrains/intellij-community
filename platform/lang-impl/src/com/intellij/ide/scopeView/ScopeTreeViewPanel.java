/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.ide.scopeView;

import com.intellij.ProjectTopics;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.scopeView.nodes.BasePsiNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.*;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 25-Jan-2006
 */
public class ScopeTreeViewPanel extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.scopeView.ScopeTreeViewPanel");
  private final IdeView myIdeView = new MyIdeView();
  private final MyPsiTreeChangeAdapter myPsiTreeChangeAdapter = new MyPsiTreeChangeAdapter();

  private final DnDAwareTree myTree = new JBTreeWithHintProvider(){
    @Override
    public boolean isFileColorsEnabled() {
      return ProjectViewTree.isFileColorsEnabledFor(this);
    }

    @Nullable
    @Override
    public Color getFileColorFor(DefaultMutableTreeNode node) {
      if (!(node instanceof PackageDependenciesNode)) {
        return null;
      }
      return ProjectViewTree.getColorForObject(((PackageDependenciesNode)node).getPsiElement(), myProject,
                                               FunctionUtil.<PsiElement>id());
    }
  };
  @NotNull
  private final Project myProject;
  private FileTreeModelBuilder myBuilder;

  private String CURRENT_SCOPE_NAME;

  private TreeExpansionMonitor<PackageDependenciesNode> myTreeExpansionMonitor;
  private CopyPasteDelegator myCopyPasteDelegator;
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
  private final DependencyValidationManager myDependencyValidationManager;
  private final WolfTheProblemSolver.ProblemListener myProblemListener = new MyProblemListener();
  private final FileStatusListener myFileStatusListener = new FileStatusListener() {
    @Override
    public void fileStatusesChanged() {
      final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myTree);
      for (TreePath treePath : treePaths) {
        final Object component = treePath.getLastPathComponent();
        if (component instanceof PackageDependenciesNode) {
          ((PackageDependenciesNode)component).updateColor();
          for (int i = 0; i< ((PackageDependenciesNode)component).getChildCount(); i++) {
            ((PackageDependenciesNode)((PackageDependenciesNode)component).getChildAt(i)).updateColor();
          }
        }
      }
    }

    @Override
    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      if (!virtualFile.isValid()) return;
      final PsiFile file = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (file != null) {
        final NamedScope currentScope = getCurrentScope();
        final PackageSet value = currentScope.getValue();
        if (value != null && value.contains(file, NamedScopesHolder.getHolder(myProject, currentScope.getName(), myDependencyValidationManager))) {
          if (!myBuilder.hasFileNode(virtualFile)) return;
          final PackageDependenciesNode node = myBuilder.getFileParentNode(virtualFile);
          final PackageDependenciesNode[] nodes = FileTreeModelBuilder.findNodeForPsiElement(node, file);
          if (nodes != null) {
            for (PackageDependenciesNode dependenciesNode : nodes) {
              dependenciesNode.updateColor();
            }
          }
        }
      }
    }
  };

  private final MergingUpdateQueue myUpdateQueue = new MergingUpdateQueue("ScopeViewUpdate", 300, isTreeShowing(), myTree);
  private final ScopeTreeViewPanel.MyChangesListListener myChangesListListener = new MyChangesListListener();
  protected ActionCallback myActionCallback;

  public ScopeTreeViewPanel(@NotNull Project project) {
    super(new BorderLayout());
    myUpdateQueue.setPassThrough(false);  // we don't want passthrough mode, even in unit tests
    myProject = project;
    initTree();

    add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myDependencyValidationManager = DependencyValidationManager.getInstance(myProject);

    final UiNotifyConnector uiNotifyConnector = new UiNotifyConnector(myTree, myUpdateQueue);
    Disposer.register(this, myUpdateQueue);
    Disposer.register(this, uiNotifyConnector);

    if (isTreeShowing()) {
      myUpdateQueue.showNotify();
    }
  }

  public void initListeners() {
    final MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyModuleRootListener());
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter);
    WolfTheProblemSolver.getInstance(myProject).addProblemListener(myProblemListener);
    ChangeListManager.getInstance(myProject).addChangeListListener(myChangesListListener);
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener, myProject);
  }

  @Override
  public void dispose() {
    FileTreeModelBuilder.clearCaches(myProject);
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeAdapter);
    WolfTheProblemSolver.getInstance(myProject).removeProblemListener(myProblemListener);
    ChangeListManager.getInstance(myProject).removeChangeListListener(myChangesListListener);
  }

  public void selectNode(final PsiElement element, final PsiFileSystemItem file, final boolean requestFocus) {
    final Runnable runnable = () -> myUpdateQueue.queue(new Update("Select") {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        PackageDependenciesNode node = myBuilder.findNode(file, element);
        if (node != null && node.getPsiElement() != element) {
          final TreePath path = new TreePath(node.getPath());
          if (myTree.isCollapsed(path)) {
            myTree.expandPath(path);
            myTree.makeVisible(path);
          }
        }
        node = myBuilder.findNode(file, element);
        if (node != null) {
          TreeUtil.selectPath(myTree, new TreePath(node.getPath()));
          if (requestFocus) {
            myTree.requestFocus();
          }
        }
      }
    });
    doWhenDone(runnable);
  }

  private void doWhenDone(Runnable runnable) {
    if (myActionCallback == null || ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      myActionCallback.doWhenDone(runnable);
    }
  }

  public void selectScope(final NamedScope scope) {
    myUpdateQueue.cancelAllUpdates();
    refreshScope(scope);
    if (scope != CustomScopesProviderEx.getAllScope() && scope != null) {
      CURRENT_SCOPE_NAME = scope.getName();
    }
  }

  public JPanel getPanel() {
    return this;
  }

  private void initTree() {
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeUtil.installActions(myTree);
    EditSourceOnDoubleClickHandler.install(myTree);
    new TreeSpeedSearch(myTree);
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, this) {
      @Override
      @NotNull
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
    myTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myTree, myProject);
    final ScopeTreeStructureExpander[] extensions = Extensions.getExtensions(ScopeTreeStructureExpander.EP_NAME, myProject);
    for (ScopeTreeStructureExpander expander : extensions) {
      myTree.addTreeWillExpandListener(expander);
    }
    if (extensions.length == 0) {
      myTree.addTreeWillExpandListener(new SortingExpandListener());
    }
    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          final Object component = myTree.getLastSelectedPathComponent();
          if (component instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)component;
            if (selectedNode.isLeaf()) {
              OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(myTree), false);
            }
          }
        }
      }
    });
  }

  @NotNull
  private PsiElement[] getSelectedPsiElements() {
    final TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null) {
      Set<PsiElement> result = new HashSet<PsiElement>();
      for (TreePath path : treePaths) {
        final Object component = path.getLastPathComponent();
        if (component instanceof PackageDependenciesNode) {
          PackageDependenciesNode node = (PackageDependenciesNode)component;
          final PsiElement psiElement = node.getPsiElement();
          if (psiElement != null && psiElement.isValid()) {
            result.add(psiElement);
          }
        }
      }
      return PsiUtilCore.toPsiElementArray(result);
    }
    return PsiElement.EMPTY_ARRAY;
  }

  public void refreshScope(@Nullable NamedScope scope) {
    FileTreeModelBuilder.clearCaches(myProject);
    if (scope == null) { //was deleted
      scope = CustomScopesProviderEx.getAllScope();
    }
    LOG.assertTrue(scope != null);
    final NamedScopesHolder holder = NamedScopesHolder.getHolder(myProject, scope.getName(), myDependencyValidationManager);
    final PackageSet packageSet = scope.getValue() != null ? scope.getValue() : new InvalidPackageSet("");
    final DependenciesPanel.DependencyPanelSettings settings = new DependenciesPanel.DependencyPanelSettings();
    settings.UI_FILTER_LEGALS = true;
    settings.UI_GROUP_BY_SCOPE_TYPE = false;
    settings.UI_SHOW_FILES = true;
    final ProjectView projectView = ProjectView.getInstance(myProject);
    settings.UI_FLATTEN_PACKAGES = projectView.isFlattenPackages(ScopeViewPane.ID);
    settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = projectView.isHideEmptyMiddlePackages(ScopeViewPane.ID);
    settings.UI_SHOW_MODULES = projectView.isShowModules(ScopeViewPane.ID);
    settings.UI_SHOW_MODULE_GROUPS = projectView.isShowModules(ScopeViewPane.ID);
    myBuilder = new FileTreeModelBuilder(myProject, new Marker() {
      @Override
      public boolean isMarked(VirtualFile file) {
        return packageSet != null && (packageSet instanceof PackageSetBase ? ((PackageSetBase)packageSet).contains(file, myProject, holder) : packageSet.contains(PackageSetBase.getPsiFile(file, myProject), holder));
      }
    }, settings);
    myTree.setPaintBusy(true);
    myBuilder.setTree(myTree);
    myTree.getEmptyText().setText("Loading...");
    myActionCallback = new ActionCallback();
    myTree.putClientProperty(TreeState.CALLBACK, new WeakReference<ActionCallback>(myActionCallback));
    myTree.setModel(myBuilder.build(myProject, true, () -> {
      myTree.setPaintBusy(false);
      myTree.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
      myActionCallback.setDone();
    }));
    ((PackageDependenciesNode)myTree.getModel().getRoot()).sortChildren();
    ((DefaultTreeModel)myTree.getModel()).reload();
    FileTreeModelBuilder.clearCaches(myProject);
  }

  protected NamedScope getCurrentScope() {
    NamedScope scope = NamedScopesHolder.getScope(myProject, CURRENT_SCOPE_NAME);
    if (scope == null) {
      scope = CustomScopesProviderEx.getAllScope();
    }
    LOG.assertTrue(scope != null);
    return scope;
  }

  @Nullable
  public Object getData(String dataId) {
    if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        PackageDependenciesNode node = (PackageDependenciesNode)selectionPath.getLastPathComponent();
        if (node instanceof ModuleNode) {
          return ((ModuleNode)node).getModule();
        }
      }
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        PackageDependenciesNode node = (PackageDependenciesNode)selectionPath.getLastPathComponent();
        return node != null && node.isValid() ? node.getPsiElement() : null;
      }
    }
    final TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null) {
      if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        Set<PsiElement> psiElements = new HashSet<PsiElement>();
        for (TreePath treePath : treePaths) {
          final PackageDependenciesNode node = (PackageDependenciesNode)treePath.getLastPathComponent();
          if (node.isValid()) {
            final PsiElement psiElement = node.getPsiElement();
            if (psiElement != null) {
              psiElements.add(psiElement);
            }
          }
        }
        return psiElements.isEmpty() ? null : PsiUtilCore.toPsiElementArray(psiElements);
      }
    }
    if (LangDataKeys.IDE_VIEW.is(dataId)) {
      return myIdeView;
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
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      if (getSelectedModules() != null) {
        return myDeleteModuleProvider;
      }
      return myDeletePSIElementProvider;
    }
    if (LangDataKeys.PASTE_TARGET_PSI_ELEMENT.is(dataId)) {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        final Object pathComponent = selectionPath.getLastPathComponent();
        if (pathComponent instanceof DirectoryNode) {
          return ((DirectoryNode)pathComponent).getTargetDirectory();
        }
      }
    }
    return null;
  }

  @Nullable
  private Module[] getSelectedModules() {
    final TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null) {
      Set<Module> result = new HashSet<Module>();
      for (TreePath path : treePaths) {
        PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
        if (node instanceof ModuleNode) {
          result.add(((ModuleNode)node).getModule());
        }
        else if (node instanceof ModuleGroupNode) {
          final ModuleGroupNode groupNode = (ModuleGroupNode)node;
          final ModuleGroup moduleGroup = groupNode.getModuleGroup();
          result.addAll(moduleGroup.modulesInGroup(myProject, true));
        }
      }
      return result.isEmpty() ? null : result.toArray(new Module[result.size()]);
    }
    return null;
  }

  private void reload(@Nullable final DefaultMutableTreeNode rootToReload) {
    final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
    if (rootToReload != null && rootToReload != treeModel.getRoot()) {
      final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myTree, new TreePath(rootToReload.getPath()));
      final List<TreePath> selectionPaths = TreeUtil.collectSelectedPaths(myTree, new TreePath(rootToReload.getPath()));
      final TreePath path = new TreePath(rootToReload.getPath());
      final boolean wasCollapsed = myTree.isCollapsed(path);
      final Runnable runnable = () -> {
        if (!isTreeShowing() || rootToReload.getParent() == null) return;
        TreeUtil.sort(rootToReload, getNodeComparator());
        treeModel.reload(rootToReload);
        if (!wasCollapsed) {
          myTree.collapsePath(path);
          for (TreePath treePath : treePaths) {
            myTree.expandPath(treePath);
          }
          for (TreePath selectionPath : selectionPaths) {
            TreeUtil.selectPath(myTree, selectionPath);
          }
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        runnable.run();
      } else {
        SwingUtilities.invokeLater(runnable);
      }
    }
    else {
      TreeUtil.sort(treeModel, getNodeComparator());
      treeModel.reload();
    }
  }

  private DependencyNodeComparator getNodeComparator() {
    return new DependencyNodeComparator(ProjectView.getInstance(myProject).isSortByType(ScopeViewPane.ID));
  }

  public void setSortByType() {
    myTreeExpansionMonitor.freeze();
    reload(null);
    myTreeExpansionMonitor.restore();
  }

  ActionCallback getActionCallback() {
    return myActionCallback;
  }

  private class MyTreeCellRenderer extends ColoredTreeCellRenderer {

    private WolfTheProblemSolver myWolfTheProblemSolver = WolfTheProblemSolver.getInstance(myProject);

    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof PackageDependenciesNode) {
        PackageDependenciesNode node = (PackageDependenciesNode)value;
        try {
          setIcon(node.getIcon());
        }
        catch (IndexNotReadyException ignore) {
        }
        final SimpleTextAttributes regularAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        TextAttributes textAttributes = regularAttributes.toTextAttributes();
        if (node instanceof BasePsiNode && ((BasePsiNode)node).isDeprecated()) {
          textAttributes =
              EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES).clone();
        }
        final PsiElement psiElement = node.getPsiElement();
        textAttributes.setForegroundColor(CopyPasteManager.getInstance().isCutElement(psiElement) ? CopyPasteManager.CUT_COLOR : node.getColor());
        if (getCurrentScope() != DefaultScopesProvider.getInstance(myProject).getProblemsScope()) {
          final PsiFile containingFile = psiElement != null ? psiElement.getContainingFile() : null;
          final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
          boolean isProblem;
          if (containingFile != null) {
            isProblem = myWolfTheProblemSolver.isProblemFile(virtualFile);
          }
          else if (virtualFile != null) {
            isProblem = myWolfTheProblemSolver.hasProblemFilesBeneath(file -> VfsUtilCore.isAncestor(virtualFile, file, false));
          }
          else {
            final Module module =  node instanceof ModuleNode ? ((ModuleNode)node).getModule() : null;
            isProblem = module != null && myWolfTheProblemSolver.hasProblemFilesBeneath(module);
          }
          if (isProblem) {
            textAttributes.setEffectColor(JBColor.RED);
            textAttributes.setEffectType(EffectType.WAVE_UNDERSCORE);
          }
        }
        append(node.toString(), SimpleTextAttributes.fromTextAttributes(textAttributes));

        String oldToString = toString();
        if (!myProject.isDisposed()) {
          for(ProjectViewNodeDecorator decorator: Extensions.getExtensions(ProjectViewNodeDecorator.EP_NAME, myProject)) {
            decorator.decorate(node, this);
          }
        }
        if (toString().equals(oldToString)) {   // nothing was decorated
          final String locationString = node.getComment();
          if (locationString != null && locationString.length() > 0) {
            append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    }
  }

  private class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
    @Override
    public void childAdded(@NotNull final PsiTreeChangeEvent event) {
      final PsiElement element = event.getParent();
      final PsiElement child = event.getChild();
      if (child == null) return;
      if (element.getContainingFile() == null) {
        queueUpdate(() -> {
          if (!child.isValid()) return;
          processNodeCreation(child);
        }, false);
      }
    }

    private void processNodeCreation(final PsiElement psiElement) {
      if (psiElement instanceof PsiFile && !isInjected((PsiFile)psiElement)) {
        final PackageDependenciesNode rootToReload = myBuilder.addFileNode((PsiFile)psiElement);
        if (rootToReload != null) {
          reload(rootToReload);
        }
      }
      else if (psiElement instanceof PsiDirectory) {
        final PsiElement[] children = psiElement.getChildren();
        if (children.length > 0) {
          queueRefreshScope(getCurrentScope(), (PsiDirectory)psiElement);
        } else {
          final PackageDependenciesNode node = myBuilder.addDirNode((PsiDirectory)psiElement);
          if (node != null) {
            reload((DefaultMutableTreeNode)node.getParent());
          }
        }
      }
    }

    @Override
    public void beforeChildRemoval(@NotNull final PsiTreeChangeEvent event) {
      final PsiElement child = event.getChild();
      final PsiElement parent = event.getParent();
      if (parent instanceof PsiDirectory && (child instanceof PsiFile && !isInjected((PsiFile)child) || child instanceof PsiDirectory)) {
        queueUpdate(() -> {
          final DefaultMutableTreeNode rootToReload = myBuilder.removeNode(child, (PsiDirectory)parent);
          if (rootToReload != null) {
            reload(rootToReload);
          }
        }, true);
      }
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
      final PsiElement oldParent = event.getOldParent();
      final PsiElement child = event.getChild();
      if (oldParent instanceof PsiDirectory) {
        if (child instanceof PsiFileSystemItem && (!(child instanceof PsiFile) || !isInjected((PsiFile)child))) {
          queueUpdate(() -> {
            final DefaultMutableTreeNode rootToReload =
              myBuilder.removeNode(child, child instanceof PsiDirectory ? (PsiDirectory)child : (PsiDirectory)oldParent);
            if (rootToReload != null) {
              reload(rootToReload);
            }
          }, true);
        }
      }
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      final PsiElement newParent = event.getNewParent();
      final PsiElement child = event.getChild();
      if (newParent instanceof PsiDirectory) {
        if (child instanceof PsiFileSystemItem && (!(child instanceof PsiFile) || !isInjected((PsiFile)child))) {
          final PsiFileSystemItem file = (PsiFileSystemItem)child;
          queueUpdate(() -> {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null && virtualFile.isValid()) {
              final PsiFileSystemItem newFile = file.isValid() ? file :
                                                (file.isDirectory() ? PsiManager.getInstance(myProject).findDirectory(virtualFile)
                                                                    : PsiManager.getInstance(myProject).findFile(virtualFile));
              if (newFile != null) {
                final PackageDependenciesNode rootToReload = newFile.isDirectory() ? myBuilder.addDirNode((PsiDirectory)newFile)
                                                                                   : myBuilder.addFileNode((PsiFile)newFile);
                if (rootToReload != null) {
                  reload(rootToReload);
                }
              }
            }
          }, true);
        }
      }
    }


    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      final PsiElement parent = event.getParent();
      final PsiFile file = parent.getContainingFile();
      if (file != null && file.getFileType() == StdFileTypes.JAVA) {
        if (!file.getViewProvider().isPhysical() && !isInjected(file)) return;
        queueUpdate(() -> {
          if (file.isValid() && file.getViewProvider().isPhysical()) {
            final NamedScope scope = getCurrentScope();
            final PackageSet packageSet = scope.getValue();
            if (packageSet == null) return; //invalid scope selected
            if (packageSet.contains(file, NamedScopesHolder.getHolder(myProject, scope.getName(), myDependencyValidationManager))){
              reload(myBuilder.getFileParentNode(file.getVirtualFile()));
            }
          }
        }, false);
      }
    }

    @Override
    public final void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      String propertyName = event.getPropertyName();
      final PsiElement element = event.getElement();
      if (element != null) {
        final NamedScope scope = getCurrentScope();
        if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME) || propertyName.equals(PsiTreeChangeEvent.PROP_FILE_TYPES)) {
          queueUpdate(() -> {
            if (element.isValid()) {
              processRenamed(scope, element.getContainingFile());
            }
          }, false);
        }
        else if (propertyName.equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)) {
          final PackageSet value = getCurrentScope().getValue();
          if (!(value instanceof PackageSetBase) || ((PackageSetBase)value).contains(((PsiDirectory)element).getVirtualFile(), myProject, myDependencyValidationManager)) {
            queueRefreshScope(scope, (PsiDirectory)element);
          }
        }
      }
    }

    @Override
    public void childReplaced(@NotNull final PsiTreeChangeEvent event) {
      final NamedScope scope = getCurrentScope();
      final PsiElement element = event.getNewChild();
      final PsiFile psiFile = event.getFile();
      if (psiFile != null && !isInjected(psiFile)) {
        if (psiFile.getLanguage() == psiFile.getViewProvider().getBaseLanguage()) {
          queueUpdate(() -> processRenamed(scope, psiFile), false);
        }
      }
      else if (element instanceof PsiDirectory && element.isValid()) {
        queueRefreshScope(scope, (PsiDirectory)element);
      }
    }

    private boolean isInjected(PsiFile psiFile) {
      return InjectedLanguageManager.getInstance(myProject).isInjectedFragment(psiFile);
    }

    private void queueRefreshScope(final NamedScope scope, final PsiDirectory dir) {
      myUpdateQueue.cancelAllUpdates();
      queueUpdate(() -> {
        myTreeExpansionMonitor.freeze();
        refreshScope(scope);
        doWhenDone(() -> {
          myTreeExpansionMonitor.restore();
          final PackageDependenciesNode dirNode = myBuilder.findNode(dir, dir);
          if (dirNode != null) {
            TreeUtil.selectPath(myTree, new TreePath(dirNode.getPath()));
          }
        });
      }, false);
    }

    private void processRenamed(final NamedScope scope, final PsiFile file) {
      if (!file.isValid() || !file.getViewProvider().isPhysical()) return;
      final PackageSet packageSet = scope.getValue();
      if (packageSet == null) return; //invalid scope selected
      if (packageSet.contains(file, NamedScopesHolder.getHolder(myProject, scope.getName(), myDependencyValidationManager))) {
        reload(myBuilder.addFileNode(file));
      }
      else {
        final DefaultMutableTreeNode rootToReload = myBuilder.removeNode(file, file.getParent());
        if (rootToReload != null) {
          reload(rootToReload);
        }
      }
    }

    //expand/collapse state should be restored in actual request if needed
    private void queueUpdate(final Runnable request, boolean updateImmediately) {
      final Runnable wrapped = () -> {
        if (myProject.isDisposed()) return;
        request.run();
      };
      if (updateImmediately && isTreeShowing()) {
        myUpdateQueue.run(new Update(request) {
          @Override
          public void run() {
            wrapped.run();
          }
        });
      }
      else {
        myUpdateQueue.queue(new Update(request) {
          @Override
          public void run() {
            wrapped.run();
          }

          @Override
          public boolean isExpired() {
            return !isTreeShowing();
          }
        });
      }
    }
  }

  private class MyModuleRootListener extends ModuleRootAdapter {
    @Override
    public void rootsChanged(ModuleRootEvent event) {
      myUpdateQueue.cancelAllUpdates();
      myUpdateQueue.queue(new Update("RootsChanged") {
        @Override
        public void run() {
          refreshScope(getCurrentScope());
        }

        @Override
        public boolean isExpired() {
          return !isTreeShowing();
        }
      });
    }
  }

  private class MyIdeView implements IdeView {
    @Override
    public void selectElement(final PsiElement element) {
      if (element != null) {
        final PackageSet packageSet = getCurrentScope().getValue();
        final PsiFile psiFile = element.getContainingFile();
        if (packageSet == null) return;
        final VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() :
                                        (element instanceof PsiDirectory ? ((PsiDirectory)element).getVirtualFile() : null);
        if (virtualFile != null) {
          final ProjectView projectView = ProjectView.getInstance(myProject);
          final NamedScopesHolder holder = NamedScopesHolder.getHolder(myProject, CURRENT_SCOPE_NAME, myDependencyValidationManager);
          if (packageSet instanceof PackageSetBase && !((PackageSetBase)packageSet).contains(virtualFile, myProject, holder) ||
              psiFile != null && !packageSet.contains(psiFile, holder)) {
            projectView.changeView(ProjectViewPane.ID);
          }
          projectView.select(element, virtualFile, false);
        }
        Editor editor = EditorHelper.openInEditor(element);
        if (editor != null) {
          ToolWindowManager.getInstance(myProject).activateEditorComponent();
        }
      }
    }

    @Nullable
    private PsiDirectory getDirectory() {
      final TreePath[] selectedPaths = myTree.getSelectionPaths();
      if (selectedPaths != null) {
        if (selectedPaths.length != 1) return null;
        TreePath path = selectedPaths[0];
        final PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
        if (!node.isValid()) return null;
        if (node instanceof DirectoryNode) {
          return (PsiDirectory)node.getPsiElement();
        }
        else if (node instanceof BasePsiNode) {
          final PsiElement psiElement = node.getPsiElement();
          LOG.assertTrue(psiElement != null);
          final PsiFile psiFile = psiElement.getContainingFile();
          LOG.assertTrue(psiFile != null);
          return psiFile.getContainingDirectory();
        }
        else if (node instanceof FileNode) {
          final PsiFile psiFile = (PsiFile)node.getPsiElement();
          return psiFile != null ? psiFile.getContainingDirectory() : null;
        }
      }
      return null;
    }

    @NotNull
    @Override
    public PsiDirectory[] getDirectories() {
      PsiDirectory directory = getDirectory();
      return directory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[]{directory};
    }

    @Override
    @Nullable
    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      final PsiElement[] elements = getSelectedPsiElements();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getSelectedPsiElements());
      ArrayList<PsiElement> validElements = new ArrayList<PsiElement>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = PsiUtilCore.toPsiElementArray(validElements);

      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }
  }

  public DnDAwareTree getTree() {
    return myTree;
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {
    @Override
    public void problemsAppeared(@NotNull VirtualFile file) {
      addNode(file, DefaultScopesProvider.getInstance(myProject).getProblemsScope().getName());
    }

    @Override
    public void problemsDisappeared(@NotNull VirtualFile file) {
      removeNode(file, DefaultScopesProvider.getInstance(myProject).getProblemsScope().getName());
    }
  }

  private void addNode(VirtualFile file, final String scopeName) {
    queueUpdate(file, psiFile -> myBuilder.addFileNode(psiFile), scopeName);
  }

  private void removeNode(VirtualFile file, final String scopeName) {
    queueUpdate(file, psiFile -> myBuilder.removeNode(psiFile, psiFile.getContainingDirectory()), scopeName);
  }

  private void queueUpdate(final VirtualFile fileToRefresh,
                           final Function<PsiFile, DefaultMutableTreeNode> rootToReloadGetter, final String scopeName) {
    if (myProject.isDisposed()) return;
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getCurrentProjectViewPane();
    if (pane == null || !ScopeViewPane.ID.equals(pane.getId()) ||
        !scopeName.equals(pane.getSubId())) {
      return;
    }
    myUpdateQueue.queue(new Update(fileToRefresh) {
      @Override
      public void run() {
        if (myProject.isDisposed() || !fileToRefresh.isValid()) return;
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(fileToRefresh);
        if (psiFile != null) {
          reload(rootToReloadGetter.fun(psiFile));
        }
      }

      @Override
      public boolean isExpired() {
        return !isTreeShowing();
      }
    });
  }

  private boolean isTreeShowing() {
    return myTree.isShowing() || ApplicationManager.getApplication().isUnitTestMode();
  }

  private class MyChangesListListener extends ChangeListAdapter {
    @Override
    public void changeListAdded(ChangeList list) {
      fireListeners(list, null);
    }

    @Override
    public void changeListRemoved(ChangeList list) {
      fireListeners(list, null);
    }

    @Override
    public void changeListRenamed(ChangeList list, String oldName) {
      fireListeners(list, oldName);
    }

    private void fireListeners(ChangeList list, @Nullable String oldName) {
      AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getCurrentProjectViewPane();
      if (pane == null || !ScopeViewPane.ID.equals(pane.getId())) {
        return;
      }
      final String subId = pane.getSubId();
      if (!list.getName().equals(subId) && (oldName == null || !oldName.equals(subId))) {
        return;
      }
      ApplicationManager.getApplication().invokeLater(() -> myDependencyValidationManager.fireScopeListeners(), myProject.getDisposed());
    }

    @Override
    public void changesRemoved(Collection<Change> changes, ChangeList fromList) {
      final String name = fromList.getName();
      final Set<VirtualFile> files = new HashSet<VirtualFile>();
      collectFiles(changes, files);
      for (VirtualFile file : files) {
        removeNode(file, name);
      }
    }

    @Override
    public void changesAdded(Collection<Change> changes, ChangeList toList) {
      final String name = toList.getName();
      final Set<VirtualFile> files = new HashSet<VirtualFile>();
      collectFiles(changes, files);
      for (VirtualFile file : files) {
        addNode(file, name);
      }
    }

    private void collectFiles(Collection<Change> changes, Set<VirtualFile> files) {
      for (Change change : changes) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
          final VirtualFile virtualFile = afterRevision.getFile().getVirtualFile();
          if (virtualFile != null) {
            files.add(virtualFile);
          }
        }
      }
    }
  }

  private class SortingExpandListener implements TreeWillExpandListener {
    @Override
    public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
      final TreePath path = event.getPath();
      if (path == null) return;
      final PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
      node.sortChildren();
      ((DefaultTreeModel)myTree.getModel()).reload(node);
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {}
  }
}
