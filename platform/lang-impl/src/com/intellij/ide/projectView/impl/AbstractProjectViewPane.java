// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.*;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.*;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.problems.ProblemListener;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.tabs.impl.SingleHeightTabs;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.project.ProjectFileNode;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static com.intellij.ide.projectView.impl.ProjectViewUtilKt.*;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SLOW_DATA_PROVIDERS;

/**
 * Allows to add additional panes to the Project view.
 * For example, Packages view or Scope view.
 *
 * @see AbstractProjectViewPSIPane
 * @see ProjectViewPane
 */
public abstract class AbstractProjectViewPane implements DataProvider, Disposable, BusyObject {
  private static final Logger LOG = Logger.getInstance(AbstractProjectViewPane.class);
  public static final ProjectExtensionPointName<AbstractProjectViewPane> EP
    = new ProjectExtensionPointName<>("com.intellij.projectViewPane");

  /**
   * @deprecated use {@link #EP} instead
   */
  @Deprecated(forRemoval = true)
  public static final ExtensionPointName<AbstractProjectViewPane> EP_NAME = new ExtensionPointName<>("com.intellij.projectViewPane");

  protected final @NotNull Project myProject;
  protected DnDAwareTree myTree;
  protected AbstractTreeStructure myTreeStructure;
  private AbstractTreeBuilder myTreeBuilder;
  private TreeExpander myTreeExpander;
  // subId->Tree state; key may be null
  private final Map<String,TreeState> myReadTreeState = new HashMap<>();
  private final AtomicBoolean myTreeStateRestored = new AtomicBoolean();
  private String mySubId;
  @NonNls private static final String ELEMENT_SUB_PANE = "subPane";
  @NonNls private static final String ATTRIBUTE_SUB_ID = "subId";

  private DnDTarget myDropTarget;
  private DnDSource myDragSource;

  private void queueUpdateByProblem() {
    if (Registry.is("projectView.showHierarchyErrors")) {
      if (myTreeBuilder != null) {
        myTreeBuilder.queueUpdate();
      }
    }
  }

  protected AbstractProjectViewPane(@NotNull Project project) {
    myProject = project;
    ProblemListener problemListener = new ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        queueUpdateByProblem();
      }

      @Override
      public void problemsChanged(@NotNull VirtualFile file) {
        queueUpdateByProblem();
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        queueUpdateByProblem();
      }
    };
    project.getMessageBus().connect(this).subscribe(ProblemListener.TOPIC, problemListener);
    Disposer.register(project, this);

    TreeStructureProvider.EP.addExtensionPointListener(project, new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull TreeStructureProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        rebuildCompletely(false);
      }

      @Override
      public void extensionRemoved(@NotNull TreeStructureProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        rebuildCompletely(true);
      }
    }, this);
    ProjectViewNodeDecorator.EP.addExtensionPointListener(project, new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull ProjectViewNodeDecorator extension, @NotNull PluginDescriptor pluginDescriptor) {
        rebuildCompletely(false);
      }

      @Override
      public void extensionRemoved(@NotNull ProjectViewNodeDecorator extension, @NotNull PluginDescriptor pluginDescriptor) {
        rebuildCompletely(true);
      }
    }, this);
  }

  private void rebuildCompletely(boolean wait) {
    ActionCallback callback = updateFromRoot(true);
    if (wait) {
      callback.waitFor(5000);
    }
    myReadTreeState.clear(); // cleanup cached tree paths
    JTree tree = getTree();
    if (tree != null) {
      tree.clearSelection();
      tree.setAnchorSelectionPath(null);
      tree.setLeadSelectionPath(null);
    }
  }

  /**
   * @deprecated unused
   */
  @Deprecated(forRemoval = true)
  protected final void fireTreeChangeListener() {
  }

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getTitle();

  public abstract @NotNull Icon getIcon();

  public abstract @NotNull String getId();

  public boolean isDefaultPane(@SuppressWarnings("unused") @NotNull Project project) {
    return false;
  }

  public final @Nullable String getSubId() {
    return mySubId;
  }

  public final void setSubId(@Nullable String subId) {
    if (Comparing.strEqual(mySubId, subId)) return;
    saveExpandedPaths();
    mySubId = subId;
    onSubIdChange();
  }

  protected void onSubIdChange() {
  }

  public boolean isInitiallyVisible() {
    return true;
  }

  public boolean supportsManualOrder() {
    return false;
  }

  protected @NotNull @ActionText String getManualOrderOptionText() {
    return IdeBundle.message("action.manual.order");
  }

  /**
   * @return all supported sub views IDs.
   * should return empty array if there is no subViews as in Project/Packages view.
   */
  public String @NotNull [] getSubIds(){
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public @NlsSafe String getPresentableSubIdName(@NotNull @NonNls String subId) {
    throw new IllegalStateException("should not call");
  }

  @NotNull
  public Icon getPresentableSubIdIcon(@NotNull String subId) {
    return getIcon();
  }

  @NotNull
  public abstract JComponent createComponent();

  public JComponent getComponentToFocus() {
    return myTree;
  }

  public void expand(final Object @Nullable [] path, final boolean requestFocus){
    if (getTreeBuilder() == null || path == null) return;
    AbstractTreeUi ui = getTreeBuilder().getUi();
    if (ui != null) ui.buildNodeForPath(path);

    DefaultMutableTreeNode node = ui == null ? null : ui.getNodeForPath(path);
    if (node == null) {
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
    if (requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
    }
    TreeUtil.selectPath(myTree, treePath);
  }

  @Override
  public void dispose() {
    if (myDropTarget != null) {
      DnDManager.getInstance().unregisterTarget(myDropTarget, myTree);
      myDropTarget = null;
    }
    if (myDragSource != null) {
      DnDManager.getInstance().unregisterSource(myDragSource, myTree);
      myDragSource = null;
    }
    setTreeBuilder(null);
    myTree = null;
    myTreeStructure = null;
  }

  @NotNull
  public abstract ActionCallback updateFromRoot(boolean restoreExpandedPaths);

  public void updateFrom(Object element, boolean forceResort, boolean updateStructure) {
    AbstractTreeBuilder builder = getTreeBuilder();
    if (builder != null) {
      builder.queueUpdateFrom(element, forceResort, updateStructure);
    }
    else if (element instanceof PsiElement) {
      AsyncProjectViewSupport support = getAsyncSupport();
      if (support != null) support.updateByElement((PsiElement)element, updateStructure);
    }
    else if (element instanceof TreePath) {
      AsyncProjectViewSupport support = getAsyncSupport();
      if (support != null) support.update((TreePath)element, updateStructure);
    }
  }

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public void selectModule(@NotNull Module module, final boolean requestFocus) {
    doSelectModuleOrGroup(module, requestFocus);
  }

  private void doSelectModuleOrGroup(@NotNull Object toSelect, final boolean requestFocus) {
    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    final Runnable runnable = () -> {
      if (requestFocus) {
        ProjectView projectView = ProjectView.getInstance(myProject);
        if (projectView != null) {
          projectView.changeView(getId(), getSubId());
        }
      }
      BaseProjectTreeBuilder builder = (BaseProjectTreeBuilder)getTreeBuilder();
      if (builder != null) {
        builder.selectInWidth(toSelect, requestFocus, node -> node instanceof AbstractModuleNode || node instanceof ModuleGroupNode || node instanceof AbstractProjectNode);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  public void selectModuleGroup(@NotNull ModuleGroup moduleGroup, boolean requestFocus) {
    doSelectModuleOrGroup(moduleGroup, requestFocus);
  }

  public TreePath[] getSelectionPaths() {
    return myTree == null ? null : myTree.getSelectionPaths();
  }

  public void addToolbarActions(@NotNull DefaultActionGroup actionGroup) {
  }

  /**
   * @return array of user objects from {@link TreePath#getLastPathComponent() last components} of the selected paths in the tree
   */
  @RequiresEdt
  public final @Nullable Object @NotNull [] getSelectedUserObjects() {
    TreePath[] paths = getSelectionPaths();
    return paths == null
           ? ArrayUtil.EMPTY_OBJECT_ARRAY
           : ArrayUtil.toObjectArray(ContainerUtil.map(paths, TreeUtil::getLastUserObject));
  }

  /**
   * @return array of user objects from single selected path in the tree,
   * or {@code null} if there is no selection or more than 1 path is selected
   */
  @RequiresEdt
  public final @Nullable Object @Nullable [] getSingleSelectedPathUserObjects() {
    TreePath singlePath = getSelectedPath();
    return singlePath == null
           ? null
           : ArrayUtil.toObjectArray(ContainerUtil.map(singlePath.getPath(), TreeUtil::getUserObject));
  }

  @NotNull
  protected <T extends NodeDescriptor<?>> List<T> getSelectedNodes(@NotNull Class<T> nodeClass) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }

    List<T> result = new ArrayList<>();
    for (TreePath path : paths) {
      T userObject = TreeUtil.getLastUserObject(nodeClass, path);
      if (userObject != null) {
        result.add(userObject);
      }
    }
    return result;
  }

  public boolean isAutoScrollEnabledFor(@NotNull VirtualFile file) {
    return true;
  }

  public boolean isFileNestingEnabled() {
    return false;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object[] selectedUserObjects = getSelectedUserObjects();

    if (SLOW_DATA_PROVIDERS.is(dataId)) {
      return slowProviders(selectedUserObjects);
    }

    Object treeStructureData = getFromTreeStructure(selectedUserObjects, dataId);
    if (treeStructureData != null) {
      return treeStructureData;
    }

    if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) return getTreeExpander();

    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;
      final ArrayList<Navigatable> navigatables = new ArrayList<>();
      for (TreePath path : paths) {
        Object node = path.getLastPathComponent();
        Object userObject = TreeUtil.getUserObject(node);
        if (userObject instanceof Navigatable) {
          navigatables.add((Navigatable)userObject);
        }
        else if (node instanceof Navigatable) {
          navigatables.add((Navigatable)node);
        }
      }
      return navigatables.isEmpty() ? null : navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    }
    return null;
  }

  // used for sorting tabs in the tabbed pane
  public abstract int getWeight();

  @NotNull
  public abstract SelectInTarget createSelectInTarget();

  public final TreePath getSelectedPath() {
    return TreeUtil.getSelectedPathIfOne(myTree);
  }

  public final NodeDescriptor getSelectedDescriptor() {
    return TreeUtil.getLastUserObject(NodeDescriptor.class, getSelectedPath());
  }

  /**
   * @see TreeUtil#getUserObject(Object)
   * @deprecated AbstractProjectViewPane#getSelectedPath
   */
  @Deprecated
  public final DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectedPath();
    return path == null ? null : ObjectUtils.tryCast(path.getLastPathComponent(), DefaultMutableTreeNode.class);
  }

  public final Object getSelectedElement() {
    final Object[] elements = getSelectedElements();
    return elements.length == 1 ? elements[0] : null;
  }

  /**
   * @deprecated use {@link #getSelectedUserObjects()} and {@link #getElementsFromNode(Object)}
   */
  @Deprecated
  public final PsiElement @NotNull [] getSelectedPSIElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    List<PsiElement> result = new ArrayList<>();
    for (TreePath path : paths) {
      result.addAll(getElementsFromNode(path.getLastPathComponent()));
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @SuppressWarnings("unchecked")
  private @Nullable Iterable<DataProvider> slowProviders(@Nullable Object @NotNull [] selectedUserObjects) {
    Iterable<DataProvider> structureProviders = (Iterable<DataProvider>)getFromTreeStructure(
      selectedUserObjects, SLOW_DATA_PROVIDERS.getName()
    );
    DataProvider selectionProvider = selectionProvider(selectedUserObjects);
    if (structureProviders != null && selectionProvider != null) {
      return ContainerUtil.nullize(ContainerUtil.flattenIterables(List.of(structureProviders, List.of(selectionProvider))));
    }
    else if (structureProviders != null) {
      return structureProviders;
    }
    else if (selectionProvider != null) {
      return List.of(selectionProvider);
    }
    else {
      return null;
    }
  }

  @RequiresEdt
  private @Nullable DataProvider selectionProvider(@Nullable Object @NotNull [] selectedUserObjects) {
    if (selectedUserObjects.length == 0) {
      return null;
    }
    Object[] singleSelectedPathUserObjects = getSingleSelectedPathUserObjects();
    return dataId -> getDataFromSelection(selectedUserObjects, singleSelectedPathUserObjects, dataId);
  }

  @RequiresReadLock(generateAssertion = false)
  @RequiresBackgroundThread(generateAssertion = false)
  private @Nullable Object getDataFromSelection(
    @Nullable Object @NotNull [] selectedUserObjects,
    @Nullable Object @Nullable [] singleSelectedPathUserObjects,
    @NotNull String dataId
  ) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement[] elements = getPsiElements(selectedUserObjects);
      return elements.length == 1 ? elements[0] : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      PsiElement[] elements = getPsiElements(selectedUserObjects);
      return elements.length > 0 ? elements : null;
    }
    if (PlatformCoreDataKeys.PROJECT_CONTEXT.is(dataId)) {
      Object selected = getSingleNodeElement(selectedUserObjects);
      return selected instanceof Project ? selected : null;
    }
    if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
      Object selected = getSingleNodeElement(selectedUserObjects);
      return moduleContext(myProject, selected);
    }
    if (LangDataKeys.MODULE_CONTEXT_ARRAY.is(dataId)) {
      return getSelectedModules(selectedUserObjects);
    }
    if (ProjectView.UNLOADED_MODULES_CONTEXT_KEY.is(dataId)) {
      return Collections.unmodifiableList(getSelectedUnloadedModules(selectedUserObjects));
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      Module[] modules = getSelectedModules(selectedUserObjects);
      if (modules != null || !getSelectedUnloadedModules(selectedUserObjects).isEmpty()) {
        return ModuleDeleteProvider.getInstance();
      }
      LibraryOrderEntry orderEntry = getSelectedLibrary(singleSelectedPathUserObjects);
      if (orderEntry != null) {
        return new DetachLibraryDeleteProvider(myProject, orderEntry);
      }
      return myDeletePSIElementProvider;
    }
    if (ModuleGroup.ARRAY_DATA_KEY.is(dataId)) {
      final List<ModuleGroup> selectedElements = getSelectedValues(selectedUserObjects, ModuleGroup.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[0]);
    }
    if (LibraryGroupElement.ARRAY_DATA_KEY.is(dataId)) {
      final List<LibraryGroupElement> selectedElements = getSelectedValues(selectedUserObjects, LibraryGroupElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new LibraryGroupElement[0]);
    }
    if (NamedLibraryElement.ARRAY_DATA_KEY.is(dataId)) {
      final List<NamedLibraryElement> selectedElements = getSelectedValues(selectedUserObjects, NamedLibraryElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new NamedLibraryElement[0]);
    }
    if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)) {
      return getSelectedValues(selectedUserObjects);
    }
    return null;
  }

  private @Nullable Object getFromTreeStructure(@Nullable Object @NotNull [] selectedUserObjects, @NotNull String dataId) {
    if (!(myTreeStructure instanceof AbstractTreeStructureBase)) {
      return null;
    }
    List<AbstractTreeNode<?>> nodes = new ArrayList<>(selectedUserObjects.length);
    for (Object userObject : selectedUserObjects) {
      if (userObject instanceof AbstractTreeNode) {
        nodes.add((AbstractTreeNode<?>)userObject);
      }
    }
    return ((AbstractTreeStructureBase)myTreeStructure).getDataFromProviders(nodes, dataId);
  }

  @RequiresReadLock(generateAssertion = false)
  @RequiresBackgroundThread(generateAssertion = false)
  private @NotNull PsiElement @NotNull [] getPsiElements(@Nullable Object @NotNull [] selectedUserObjects) {
    List<PsiElement> result = new ArrayList<>();
    for (Object userObject : selectedUserObjects) {
      ContainerUtil.addAllNotNull(result, getElementsFromNode(userObject));
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  private static @Nullable Object getSingleNodeElement(@Nullable Object @NotNull [] selectedUserObjects) {
    if (selectedUserObjects.length != 1) {
      return null;
    }
    return getNodeElement(selectedUserObjects[0]);
  }

  private @NotNull Module @Nullable [] getSelectedModules(@Nullable Object @NotNull [] selectedUserObjects) {
    List<Module> result = moduleContexts(myProject, getSelectedValues(selectedUserObjects));
    return result.isEmpty() ? null : result.toArray(Module.EMPTY_ARRAY);
  }

  private @NotNull List<@NotNull UnloadedModuleDescription> getSelectedUnloadedModules(@Nullable Object @NotNull [] selectedUserObjects) {
    return unloadedModules(myProject, getSelectedValues(selectedUserObjects));
  }

  private <T> @NotNull List<@NotNull T> getSelectedValues(@Nullable Object @NotNull [] selectedUserObjects, @NotNull Class<T> aClass) {
    return ContainerUtil.filterIsInstance(getSelectedValues(selectedUserObjects), aClass);
  }

  public final @NotNull Object @NotNull [] getSelectedValues(@Nullable Object @NotNull [] selectedUserObjects) {
    List<@NotNull Object> result = new ArrayList<>(selectedUserObjects.length);
    for (Object userObject : selectedUserObjects) {
      Object valueFromNode = getValueFromNode(userObject);
      if (valueFromNode instanceof Object[]) {
        for (Object value : (Object[])valueFromNode) {
          if (value != null) {
            result.add(value);
          }
        }
      }
      else if (valueFromNode != null) {
        result.add(valueFromNode);
      }
    }
    return ArrayUtil.toObjectArray(result);
  }

  private @Nullable PsiElement getFirstElementFromNode(@Nullable Object node) {
    return ContainerUtil.getFirstItem(getElementsFromNode(node));
  }

  @NotNull
  public List<PsiElement> getElementsFromNode(@Nullable Object node) {
    Object value = getValueFromNode(node);
    JBIterable<?> it = value instanceof PsiElement || value instanceof VirtualFile ? JBIterable.of(value) :
                       value instanceof Object[] ? JBIterable.of((Object[])value) :
                       value instanceof Iterable ? JBIterable.from((Iterable<?>)value) :
                       JBIterable.of(TreeUtil.getUserObject(node));
    return it.flatten(o -> o instanceof RootsProvider ? ((RootsProvider)o).getRoots() : Collections.singleton(o))
      .map(o -> o instanceof VirtualFile ? PsiUtilCore.findFileSystemItem(myProject, (VirtualFile)o) : o)
      .filter(PsiElement.class)
      .filter(PsiElement::isValid)
      .toList();
  }

  @Nullable
  protected Module getNodeModule(@Nullable final Object element) {
    if (element instanceof PsiElement) {
      PsiElement psiElement = (PsiElement)element;
      return ModuleUtilCore.findModuleForPsiElement(psiElement);
    }
    return null;
  }

  /**
   * @deprecated use {@link #getSelectedUserObjects()} and {@link #getSelectedValues(Object[])}
   */
  @Deprecated
  public final Object @NotNull [] getSelectedElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    ArrayList<Object> list = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      Object element = getValueFromNode(lastPathComponent);
      if (element instanceof Object[]) {
        Collections.addAll(list, (Object[])element);
      }
      else if (element != null) {
        list.add(element);
      }
    }
    return ArrayUtil.toObjectArray(list);
  }

  @Nullable
  public Object getValueFromNode(@Nullable Object node) {
    return extractValueFromNode(node);
  }

  @Nullable
  public static Object extractValueFromNode(@Nullable Object node) {
    Object userObject = TreeUtil.getUserObject(node);
    Object element = null;
    if (userObject instanceof AbstractTreeNode) {
      AbstractTreeNode descriptor = (AbstractTreeNode)userObject;
      element = descriptor.getValue();
    }
    else if (userObject instanceof NodeDescriptor) {
      NodeDescriptor descriptor = (NodeDescriptor)userObject;
      element = descriptor.getElement();
      if (element instanceof AbstractTreeNode) {
        element = ((AbstractTreeNode<?>)element).getValue();
      }
    }
    else if (userObject != null) {
      element = userObject;
    }
    return element;
  }

  public final AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  public void readExternal(@NotNull Element element)  {
    List<Element> subPanes = element.getChildren(ELEMENT_SUB_PANE);
    for (Element subPane : subPanes) {
      String subId = subPane.getAttributeValue(ATTRIBUTE_SUB_ID);
      TreeState treeState = TreeState.createFrom(subPane);
      if (!treeState.isEmpty()) {
        myReadTreeState.put(subId, treeState);
      }
    }
  }

  public void writeExternal(Element element) {
    saveExpandedPaths();
    for (Map.Entry<String, TreeState> entry : myReadTreeState.entrySet()) {
      String subId = entry.getKey();
      TreeState treeState = entry.getValue();
      Element subPane = new Element(ELEMENT_SUB_PANE);
      if (subId != null) {
        subPane.setAttribute(ATTRIBUTE_SUB_ID, subId);
      }
      treeState.writeExternal(subPane);
      element.addContent(subPane);
    }
  }

  protected @NotNull TreeState createTreeState(@NotNull JTree tree) {
    return TreeState.createOn(tree);
  }

  protected void saveExpandedPaths() {
    myTreeStateRestored.set(false);
    if (myTree != null) {
      TreeState treeState = createTreeState(myTree);
      if (!treeState.isEmpty()) {
        myReadTreeState.put(getSubId(), treeState);
      }
      else {
        myReadTreeState.remove(getSubId());
      }
    }
  }

  public final void restoreExpandedPaths(){
    if (myTree == null || myTreeStateRestored.getAndSet(true)) return;
    TreeState treeState = myReadTreeState.get(getSubId());
    if (treeState != null && !treeState.isEmpty()) {
      treeState.applyTo(myTree);
    }
    else if (myTree.isSelectionEmpty()) {
      TreeUtil.promiseSelectFirst(myTree);
    }
  }


  private @NotNull TreeExpander getTreeExpander() {
    TreeExpander expander = myTreeExpander;
    if (expander == null) {
      expander = createTreeExpander();
      myTreeExpander = expander;
    }
    return expander;
  }

  protected @NotNull TreeExpander createTreeExpander() {
    return new DefaultTreeExpander(this::getTree) {
      private boolean isExpandAllAllowed() {
        JTree tree = getTree();
        TreeModel model = tree == null ? null : tree.getModel();
        return model == null || model instanceof AsyncTreeModel || model instanceof InvokerSupplier;
      }

      @Override
      public boolean isExpandAllVisible() {
        return isExpandAllAllowed() && Registry.is("ide.project.view.expand.all.action.visible");
      }

      @Override
      public boolean canExpand() {
        return isExpandAllAllowed() && super.canExpand();
      }

      @Override
      protected void collapseAll(@NotNull JTree tree, boolean strict, int keepSelectionLevel) {
        super.collapseAll(tree, false, keepSelectionLevel);
      }
    };
  }


  protected @NotNull Comparator<NodeDescriptor<?>> createComparator() {
    return new GroupByTypeComparator(myProject, getId());
  }

  public void installComparator() {
    installComparator(getTreeBuilder());
  }

  void installComparator(AbstractTreeBuilder treeBuilder) {
    installComparator(treeBuilder, createComparator());
  }

  @TestOnly
  public void installComparator(@NotNull Comparator<? super NodeDescriptor<?>> comparator) {
    installComparator(getTreeBuilder(), comparator);
  }

  protected void installComparator(AbstractTreeBuilder builder, @NotNull Comparator<? super NodeDescriptor<?>> comparator) {
    if (builder != null) {
      builder.setNodeDescriptorComparator(comparator);
    }
  }

  public JTree getTree() {
    return myTree;
  }

  public PsiDirectory @NotNull [] getSelectedDirectories() {
    List<PsiDirectory> directories = new ArrayList<>();
    for (PsiDirectoryNode node : getSelectedNodes(PsiDirectoryNode.class)) {
      PsiDirectory directory = node.getValue();
      if (directory != null) {
        directories.add(directory);
        Object parentValue = node.getParent().getValue();
        if (parentValue instanceof PsiDirectory && Registry.is("projectView.choose.directory.on.compacted.middle.packages")) {
          while (true) {
            directory = directory.getParentDirectory();
            if (directory == null || directory.equals(parentValue)) {
              break;
            }
            directories.add(directory);
          }
        }
      }
    }
    if (!directories.isEmpty()) {
      return directories.toArray(PsiDirectory.EMPTY_ARRAY);
    }

    final PsiElement[] elements = getSelectedPSIElements();
    if (elements.length == 1) {
      final PsiElement element = elements[0];
      if (element instanceof PsiDirectory) {
        return new PsiDirectory[]{(PsiDirectory)element};
      }
      else if (element instanceof PsiDirectoryContainer) {
        return ((PsiDirectoryContainer)element).getDirectories();
      }
      else {
        final PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
          final PsiDirectory psiDirectory = containingFile.getContainingDirectory();
          if (psiDirectory != null) {
            return new PsiDirectory[]{psiDirectory};
          }
          final VirtualFile file = containingFile.getVirtualFile();
          if (file instanceof VirtualFileWindow) {
            final VirtualFile delegate = ((VirtualFileWindow)file).getDelegate();
            final PsiFile delegatePsiFile = containingFile.getManager().findFile(delegate);
            if (delegatePsiFile != null && delegatePsiFile.getContainingDirectory() != null) {
              return new PsiDirectory[] { delegatePsiFile.getContainingDirectory() };
            }
          }
          return PsiDirectory.EMPTY_ARRAY;
        }
      }
    }
    else {
      TreePath path = getSelectedPath();
      if (path != null) {
        Object component = path.getLastPathComponent();
        if (component instanceof DefaultMutableTreeNode) {
          return getSelectedDirectoriesInAmbiguousCase(((DefaultMutableTreeNode)component).getUserObject());
        }
        return getSelectedDirectoriesInAmbiguousCase(component);
      }
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  protected PsiDirectory @NotNull [] getSelectedDirectoriesInAmbiguousCase(Object userObject) {
    if (userObject instanceof AbstractModuleNode) {
      final Module module = ((AbstractModuleNode)userObject).getValue();
      if (module != null && !module.isDisposed()) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
        List<PsiDirectory> dirs = new ArrayList<>(sourceRoots.length);
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (final VirtualFile sourceRoot : sourceRoots) {
          final PsiDirectory directory = psiManager.findDirectory(sourceRoot);
          if (directory != null) {
            dirs.add(directory);
          }
        }
        return dirs.toArray(PsiDirectory.EMPTY_ARRAY);
      }
    }
    else if (userObject instanceof ProjectViewNode) {
      VirtualFile file = ((ProjectViewNode<?>)userObject).getVirtualFile();
      if (file != null && file.isValid() && file.isDirectory()) {
        PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(file);
        if (directory != null) {
          return new PsiDirectory[]{directory};
        }
      }
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  // Drag'n'Drop stuff

  public static PsiElement @Nullable [] getTransferedPsiElements(@NotNull Transferable transferable) {
    try {
      final Object transferData = transferable.getTransferData(DnDEventImpl.ourDataFlavor);
      if (transferData instanceof TransferableWrapper) {
        return ((TransferableWrapper)transferData).getPsiElements();
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }

   public static TreeNode @Nullable [] getTransferedTreeNodes(@NotNull Transferable transferable) {
    try {
      final Object transferData = transferable.getTransferData(DnDEventImpl.ourDataFlavor);
      if (transferData instanceof TransferableWrapper) {
        return ((TransferableWrapper)transferData).getTreeNodes();
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }

  protected void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myDropTarget = new ProjectViewDropTarget(myTree, myProject) {
        @Nullable
        @Override
        protected PsiElement getPsiElement(@NotNull TreePath path) {
          return getFirstElementFromNode(path.getLastPathComponent());
        }

        @Nullable
        @Override
        protected Module getModule(@NotNull PsiElement element) {
          return getNodeModule(element);
        }

        @Override
        public void cleanUpOnLeave() {
          beforeDnDLeave();
          super.cleanUpOnLeave();
        }

        @Override
        public boolean update(DnDEvent event) {
          beforeDnDUpdate(event);
          return super.update(event);
        }
      };
      myDragSource = new MyDragSource();
      DnDManager dndManager = DnDManager.getInstance();
      dndManager.registerSource(myDragSource, myTree);
      dndManager.registerTarget(myDropTarget, myTree);
    }
  }

  protected void beforeDnDUpdate(DnDEvent event) { }

  protected void beforeDnDLeave() { }

  public void setTreeBuilder(final AbstractTreeBuilder treeBuilder) {
    if (treeBuilder != null) {
      Disposer.register(this, treeBuilder);
// needs refactoring for project view first
//      treeBuilder.setCanYieldUpdate(true);
    }
    myTreeBuilder = treeBuilder;
  }

  @ApiStatus.Internal
  public boolean supportsAbbreviatePackageNames() {
    return true;
  }

  @ApiStatus.Internal
  public boolean supportsCompactDirectories() {
    return false;
  }

  @ApiStatus.Internal
  public boolean supportsFlattenModules() {
    return false;
  }

  @ApiStatus.Internal
  public boolean supportsFoldersAlwaysOnTop() {
    return true;
  }

  @ApiStatus.Internal
  public boolean supportsHideEmptyMiddlePackages() {
    return true;
  }

  @ApiStatus.Internal
  public boolean supportsShowExcludedFiles() {
    return false;
  }

  @ApiStatus.Internal
  public boolean supportsShowLibraryContents() {
    return false;
  }

  @ApiStatus.Internal
  public boolean supportsShowModules() {
    return false;
  }

  @ApiStatus.Internal
  public boolean supportsSortByType() {
    return true;
  }

  @NotNull
   private static Color getFileForegroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditorManager manager = FileEditorManager.getInstance(project);
    if (manager instanceof FileEditorManagerImpl) {
      return ((FileEditorManagerImpl)manager).getFileColor(file);
    }
    return UIUtil.getLabelForeground();
  }

  private final class MyDragSource implements DnDSource {
    @Override
    public boolean canStartDragging(DnDAction action, @NotNull Point dragOrigin) {
      if ((action.getActionId() & DnDConstants.ACTION_COPY_OR_MOVE) == 0) return false;
      final Object[] elements = getSelectedElements();
      final PsiElement[] psiElements = getSelectedPSIElements();
      DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      return psiElements.length > 0 || canDragElements(elements, dataContext, action.getActionId());
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, @NotNull Point dragOrigin) {
      PsiElement[] psiElements = getSelectedPSIElements();
      TreePath[] paths = getSelectionPaths();
      return new DnDDragStartBean(new TransferableWrapper() {
        @Override
        public List<File> asFileList() {
          return PsiCopyPasteManager.asFileList(psiElements);
        }

        @Override
        public TreePath @Nullable [] getTreePaths() {
          return paths;
        }

        @Override
        public TreeNode[] getTreeNodes() {
          return TreePathUtil.toTreeNodes(getTreePaths());
        }

        @Override
        public PsiElement[] getPsiElements() {
          return psiElements;
        }
      });
    }

    // copy/paste from com.intellij.ide.dnd.aware.DnDAwareTree.createDragImage
    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @NotNull DnDDragStartBean bean) {
      final TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;

      List<Trinity<@Nls String, Icon, @Nullable VirtualFile>> toRender = new ArrayList<>();
      for (TreePath path : getSelectionPaths()) {
        Pair<Icon, @Nls String> iconAndText = getIconAndText(path);
        toRender.add(Trinity.create(iconAndText.second, iconAndText.first,
                                    PsiCopyPasteManager.asVirtualFile(getFirstElementFromNode(path.getLastPathComponent()))));
      }

      int count = 0;
      JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
      int maxItemsToShow = toRender.size() < 20 ? toRender.size() : 10;
      for (Trinity<@Nls String, Icon, @Nullable VirtualFile> trinity : toRender) {
        JLabel fileLabel = new DragImageLabel(trinity.first, trinity.second, trinity.third);
        panel.add(fileLabel);
        count++;
        if (count > maxItemsToShow) {
          panel.add(new DragImageLabel(IdeBundle.message("label.more.files", paths.length - maxItemsToShow), EmptyIcon.ICON_16, null));
          break;
        }
      }
      panel.setSize(panel.getPreferredSize());
      panel.doLayout();

      BufferedImage image = ImageUtil.createImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();
      panel.paint(g2);
      g2.dispose();

      return new Pair<>(image, new Point());
    }

    @NotNull
    private Pair<Icon, @Nls String> getIconAndText(TreePath path) {
      Object object = TreeUtil.getLastUserObject(path);
      Component component = getTree().getCellRenderer()
        .getTreeCellRendererComponent(getTree(), object, false, false, true, getTree().getRowForPath(path), false);
      Icon[] icon = new Icon[1];
      String[] text = new String[1];
      ObjectUtils.consumeIfCast(component, ProjectViewRenderer.class, renderer -> icon[0] = renderer.getIcon());
      ObjectUtils.consumeIfCast(component, SimpleColoredComponent.class, renderer -> text[0] = renderer.getCharSequence(true).toString());
      return Pair.create(icon[0], text[0]);
    }
  }

  private class DragImageLabel extends JLabel {
    private DragImageLabel(@Nls String text, Icon icon, @Nullable VirtualFile file) {
      super(text, icon, SwingConstants.LEADING);
      setFont(UIUtil.getTreeFont());
      setOpaque(true);
      if (file != null) {
        setBackground(EditorTabPresentationUtil.getEditorTabBackgroundColor(myProject, file));
        setForeground(getFileForegroundColor(myProject, file));
      } else {
        setForeground(RenderingUtil.getForeground(getTree(), true));
        setBackground(RenderingUtil.getBackground(getTree(), true));
      }
      setBorder(new EmptyBorder(JBUI.CurrentTheme.EditorTabs.tabInsets()));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = JBUI.scale(SingleHeightTabs.UNSCALED_PREF_HEIGHT);
      return size;
    }
  }

  private static boolean canDragElements(Object @NotNull [] elements, @NotNull DataContext dataContext, int dragAction) {
    for (Object element : elements) {
      if (element instanceof Module) {
        return true;
      }
    }
    return dragAction == DnDConstants.ACTION_MOVE && MoveHandler.canMove(dataContext);
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    if (myTreeBuilder == null) return ActionCallback.DONE;
    if (myTreeBuilder.isDisposed()) return ActionCallback.REJECTED;
    return myTreeBuilder.getUi().getReady(requestor);
  }

  /**
   * @deprecated temporary API
   */
  @TestOnly
  @Deprecated(forRemoval = true)
  @NotNull
  public Promise<TreePath> promisePathToElement(@NotNull Object element) {
    AbstractTreeBuilder builder = getTreeBuilder();
    if (builder != null) {
      DefaultMutableTreeNode node = builder.getNodeForElement(element);
      if (node == null) return Promises.rejectedPromise();
      return Promises.resolvedPromise(new TreePath(node.getPath()));
    }
    TreeVisitor visitor = createVisitor(element);
    if (visitor == null || myTree == null) return Promises.rejectedPromise();
    return TreeUtil.promiseVisit(myTree, visitor);
  }

  @ApiStatus.Internal
  public boolean isVisibleAndSelected(Object element) {
    JTree tree = getTree();
    if (tree == null) return false;
    TreePath path = TreeUtil.getSelectedPathIfOne(tree);
    if (path == null) return false;
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return false;
    Rectangle visible = tree.getVisibleRect();
    if (bounds.y < visible.y || bounds.y > visible.y + visible.height - bounds.height) return false;
    AbstractTreeNode<?> node = TreeUtil.getLastUserObject(AbstractTreeNode.class, path);
    return node != null && node.canRepresent(element);
  }

  AsyncProjectViewSupport getAsyncSupport() {
    return null;
  }

  private final DeleteProvider myDeletePSIElementProvider = new ProjectViewDeleteElementProvider() {

    @Override
    protected PsiElement @NotNull [] getSelectedPSIElements(@NotNull DataContext dataContext) {
      return AbstractProjectViewPane.this.getSelectedPSIElements();
    }

    @Override
    protected Boolean hideEmptyMiddlePackages(@NotNull DataContext dataContext) {
      Project project = dataContext.getData(CommonDataKeys.PROJECT);
      return project != null && ProjectView.getInstance(project).isHideEmptyMiddlePackages(AbstractProjectViewPane.this.getId());
    }
  };

  @NotNull
  static List<TreeVisitor> createVisitors(Object @NotNull ... objects) {
    return StreamEx.of(objects).map(AbstractProjectViewPane::createVisitor).nonNull().toImmutableList();
  }

  @Nullable
  public static TreeVisitor createVisitor(@NotNull Object object) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
    }
    if (object instanceof ProjectFileNode) {
      ProjectFileNode node = (ProjectFileNode)object;
      object = node.getVirtualFile();
    }
    if (object instanceof VirtualFile) return createVisitor((VirtualFile)object);
    if (object instanceof PsiElement) return createVisitor((PsiElement)object);
    LOG.warn("unsupported object: " + object);
    return null;
  }

  @NotNull
  public static TreeVisitor createVisitor(@NotNull VirtualFile file) {
    return createVisitor(null, file);
  }

  @Nullable
  public static TreeVisitor createVisitor(@NotNull PsiElement element) {
    return createVisitor(element, null);
  }

  @Nullable
  public static TreeVisitor createVisitor(@Nullable PsiElement element, @Nullable VirtualFile file) {
    return createVisitor(element, file, null);
  }

  @Nullable
  static TreeVisitor createVisitor(@Nullable PsiElement element, @Nullable VirtualFile file, @Nullable List<? super TreePath> collector) {
    Predicate<? super TreePath> predicate = collector == null ? null : path -> {
      collector.add(path);
      return false;
    };
    if (element != null && element.isValid()) return new ProjectViewNodeVisitor(element, file, predicate);
    if (file != null) return new ProjectViewFileVisitor(file, predicate);
    LOG.warn(element != null ? "element invalidated: " + element : "cannot create visitor without element and/or file");
    return null;
  }
}