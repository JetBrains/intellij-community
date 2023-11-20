// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiAwareObject;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.move.MoveHandler;
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
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
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
import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.BGT_DATA_PROVIDER;

/**
 * Allows to add additional panes to the Project view.
 * For example, Packages view or Scope view.
 *
 * @see AbstractProjectViewPaneWithAsyncSupport
 * @see ProjectViewPane
 */
public abstract class AbstractProjectViewPane implements DataProvider, Disposable, BusyObject {
  private static final Logger LOG = Logger.getInstance(AbstractProjectViewPane.class);
  public static final ProjectExtensionPointName<AbstractProjectViewPane> EP
    = new ProjectExtensionPointName<>("com.intellij.projectViewPane");

  protected final @NotNull Project myProject;
  protected DnDAwareTree myTree;
  protected AbstractTreeStructure myTreeStructure;
  private TreeExpander myTreeExpander;
  // subId->Tree state; key may be null
  private final Map<String,TreeState> myReadTreeState = new HashMap<>();
  private final AtomicBoolean myTreeStateRestored = new AtomicBoolean();
  private String mySubId;
  @NonNls private static final String ELEMENT_SUB_PANE = "subPane";
  @NonNls private static final String ATTRIBUTE_SUB_ID = "subId";

  private DnDTarget myDropTarget;
  private DnDSource myDragSource;

  protected AbstractProjectViewPane(@NotNull Project project) {
    myProject = project;
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
    myTree = null;
    myTreeStructure = null;
  }

  @NotNull
  public abstract ActionCallback updateFromRoot(boolean restoreExpandedPaths);

  public void updateFrom(Object element, boolean forceResort, boolean updateStructure) {
    if (element instanceof PsiElement) {
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
    throw new UnsupportedOperationException("Not implemented");
  }

  public void selectModuleGroup(@NotNull ModuleGroup moduleGroup, boolean requestFocus) {
    doSelectModuleOrGroup(moduleGroup, requestFocus);
  }

  public TreePath @Nullable [] getSelectionPaths() {
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

  /** @deprecated Use {@link #getSelectionPaths()} */
  @Deprecated(forRemoval = true)
  protected @NotNull <T extends NodeDescriptor<?>> List<T> getSelectedNodes(@NotNull Class<T> nodeClass) {
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

  public boolean isAutoScrollEnabledWithoutFocus() {
    return false;
  }

  public boolean isFileNestingEnabled() {
    return false;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (PROJECT.is(dataId)) {
      return myProject;
    }

    Object[] selectedUserObjects = getSelectedUserObjects();

    if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)) {
      return selectedUserObjects;
    }
    if (PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.is(dataId)) {
      return FileEditorManagerEx.getInstanceEx(myProject).getSelectedEditor();
    }

    if (BGT_DATA_PROVIDER.is(dataId)) {
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

  /** @see TreeUtil#getLastUserObject */
  public final @Nullable TreePath getSelectedPath() {
    return TreeUtil.getSelectedPathIfOne(myTree);
  }

  /** @deprecated Use {@link #getSelectedPath} */
  @Deprecated(forRemoval = true)
  public final @Nullable NodeDescriptor getSelectedDescriptor() {
    return TreeUtil.getLastUserObject(NodeDescriptor.class, getSelectedPath());
  }

  /** @deprecated Use {@link #getSelectedPath} */
  @Deprecated(forRemoval = true)
  public final DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectedPath();
    return path == null ? null : ObjectUtils.tryCast(path.getLastPathComponent(), DefaultMutableTreeNode.class);
  }

  /** @deprecated Use {@link #getSelectedUserObjects()} and {@link #getElementsFromNode(Object)} */
  @Deprecated(forRemoval = true)
  public final Object getSelectedElement() {
    final Object[] elements = getSelectedElements();
    return elements.length == 1 ? elements[0] : null;
  }

  /** @deprecated Use {@link #getSelectedUserObjects()} and {@link #getElementsFromNode(Object)} */
  @Deprecated(forRemoval = true)
  public final PsiElement @NotNull [] getSelectedPSIElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    List<PsiElement> result = new ArrayList<>();
    for (TreePath path : paths) {
      result.addAll(getElementsFromNode(path.getLastPathComponent()));
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  private @Nullable DataProvider slowProviders(@Nullable Object @NotNull [] selectedUserObjects) {
    DataProvider structureProvider = (DataProvider)getFromTreeStructure(selectedUserObjects, BGT_DATA_PROVIDER.getName());
    DataProvider selectionProvider = selectionProvider(selectedUserObjects);
    return structureProvider != null ? CompositeDataProvider.compose(structureProvider, selectionProvider) : selectionProvider;
  }

  @RequiresEdt
  private @Nullable DataProvider selectionProvider(@Nullable Object @NotNull [] selectedUserObjects) {
    if (selectedUserObjects.length == 0) {
      return null;
    }
    Object[] singleSelectedPathUserObjects = getSingleSelectedPathUserObjects();
    return dataId -> getSlowDataFromSelection(selectedUserObjects, singleSelectedPathUserObjects, dataId);
  }

  @RequiresReadLock(generateAssertion = false)
  @RequiresBackgroundThread(generateAssertion = false)
  protected @Nullable Object getSlowDataFromSelection(@Nullable Object @NotNull [] selectedUserObjects,
                                                      @Nullable Object @Nullable [] singleSelectedPathUserObjects,
                                                      @NotNull String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement[] elements = getPsiElements(selectedUserObjects);
      return elements.length == 1 ? elements[0] : null;
    }
    if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
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
    JBIterable<?> it = value instanceof PsiElement || value instanceof VirtualFile || value instanceof PsiAwareObject ? JBIterable.of(value) :
                       value instanceof Object[] ? JBIterable.of((Object[])value) :
                       value instanceof Iterable ? JBIterable.from((Iterable<?>)value) :
                       JBIterable.of(TreeUtil.getUserObject(node));
    return it.flatten(o -> o instanceof RootsProvider ? ((RootsProvider)o).getRoots() : Collections.singleton(o))
      .map(o -> o instanceof VirtualFile
                ? PsiUtilCore.findFileSystemItem(myProject, (VirtualFile)o)
                : o instanceof PsiAwareObject
                  ? ((PsiAwareObject)o).findElement(myProject)
                  : o)
      .filter(PsiElement.class)
      .filter(PsiElement::isValid)
      .toList();
  }

  @Nullable
  protected Module getNodeModule(@Nullable final Object element) {
    if (element instanceof PsiElement psiElement) {
      return ModuleUtilCore.findModuleForPsiElement(psiElement);
    }
    return null;
  }

  /** @deprecated use {@link #getSelectedUserObjects()} and {@link #getSelectedValues(Object[])} */
  @Deprecated(forRemoval = true)
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
    installComparator(createComparator());
  }

  public void installComparator(@NotNull Comparator<? super NodeDescriptor<?>> comparator) {
  }

  public JTree getTree() {
    return myTree;
  }

  @Deprecated
  public PsiDirectory @NotNull [] getSelectedDirectories() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiDirectory.EMPTY_ARRAY;
    Object [] selectedUserObjects = ContainerUtil.map2Array(paths, TreeUtil::getLastUserObject);
    if (selectedUserObjects.length == 0) return PsiDirectory.EMPTY_ARRAY;
    return getSelectedDirectories(selectedUserObjects);
  }

  @RequiresBackgroundThread(generateAssertion = false)
  protected PsiDirectory @NotNull [] getSelectedDirectories(Object @NotNull[] selectedUserObjects) {
    List<PsiDirectory> directories = new ArrayList<>();
    for (Object obj : selectedUserObjects) {
      PsiDirectoryNode node = ObjectUtils.tryCast(obj, PsiDirectoryNode.class);
      if (node != null) {
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
    }
    if (!directories.isEmpty()) {
      return directories.toArray(PsiDirectory.EMPTY_ARRAY);
    }

    List<PsiElement> elements = new ArrayList<>(selectedUserObjects.length);
    for (Object node : selectedUserObjects) {
      elements.addAll(getElementsFromNode(node));
    }

    if (elements.size() == 1) {
      final PsiElement element = elements.get(0);
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
              return new PsiDirectory[]{delegatePsiFile.getContainingDirectory()};
            }
          }
          return PsiDirectory.EMPTY_ARRAY;
        }
      }
    }
    else if (selectedUserObjects.length == 1) {
      return getSelectedDirectoriesInAmbiguousCase(selectedUserObjects[0]);
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
  public boolean supportsShowScratchesAndConsoles() {
    return false;
  }

  @ApiStatus.Internal
  public boolean supportsSortByType() {
    return true;
  }

  @ApiStatus.Internal
  public boolean supportsSortByTime() {
    return true;
  }

  @ApiStatus.Internal
  public final boolean supportsSortKey(@NotNull NodeSortKey sortKey) {
    return switch (sortKey) {
      case BY_NAME -> true;
      case BY_TYPE -> supportsSortByType();
      case BY_TIME_DESCENDING, BY_TIME_ASCENDING -> supportsSortByTime();
    };
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
      var selectedObjects = getSelectedUserObjects();
      for (Object object : selectedObjects) {
        if (object instanceof AbstractPsiBasedNode<?> || object instanceof AbstractModuleNode) {
          return true;
        }
      }
      DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      return canDrag(dataContext, action.getActionId());
    }

    private static boolean canDrag(@NotNull DataContext dataContext, int dragAction) {
      return dragAction == DnDConstants.ACTION_MOVE && MoveHandler.canMove(dataContext);
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

    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @NotNull DnDDragStartBean bean) {
      try {
        ProjectViewRendererKt.setGrayedTextPaintingEnabled(false);
        final TreePath[] paths = getSelectionPaths();
        var tree = getTree();
        if (tree == null || paths == null || paths.length == 0) return null;
        var dragImageRows = createDragImageRows(tree, paths);
        BufferedImage image = paintDragImageRows(tree, dragImageRows);
        return new Pair<>(image, new Point());
      }
      finally {
        ProjectViewRendererKt.setGrayedTextPaintingEnabled(true);
      }
    }

    @NotNull
    private static ArrayList<DragImageRow> createDragImageRows(@NotNull JTree tree, @Nullable TreePath @NotNull [] paths) {
      var count = 0;
      int maxItemsToShow = paths.length < 20 ? paths.length : 10;
      var dragImageRows = new ArrayList<DragImageRow>();
      for (TreePath path : paths) {
        dragImageRows.add(new NodeRow(tree, path));
        count++;
        if (count > maxItemsToShow) {
          dragImageRows.add(new MoreFilesRow(tree, paths.length - maxItemsToShow));
          break;
        }
      }
      return dragImageRows;
    }

    @NotNull
    private static BufferedImage paintDragImageRows(@NotNull JTree tree, @NotNull ArrayList<DragImageRow> dragImageRows) {
      var totalHeight = 0;
      var maxWidth = 0;
      for (var row : dragImageRows) {
        var size = row.getSize();
        maxWidth = Math.max(maxWidth, size.width);
        totalHeight += size.height;
      }
      var gc = tree.getGraphicsConfiguration();
      BufferedImage image = ImageUtil.createImage(gc, maxWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = (Graphics2D)image.getGraphics();
      try {
        for (var row : dragImageRows) {
          row.paint(g);
          g.translate(0, row.getSize().height);
        }
      }
      finally {
        g.dispose();
      }
      return image;
    }

    private abstract static class DragImageRow {
      abstract @NotNull Dimension getSize();
      abstract void paint(@NotNull Graphics2D g);
    }

    private static final class NodeRow extends DragImageRow {
      private final @NotNull JTree tree;
      private final @Nullable TreePath path;
      private @Nullable Dimension size;

      NodeRow(@NotNull JTree tree, @Nullable TreePath path) {
        this.tree = tree;
        this.path = path;
      }

      @Override
      @NotNull
      Dimension getSize() {
        var size = this.size;
        if (size == null) {
          size = getRenderer(tree, path).getPreferredSize();
          this.size = size;
        }
        return size;
      }

      @Override
      void paint(@NotNull Graphics2D g) {
        var renderer = getRenderer(tree, path);
        renderer.setSize(getSize());
        renderer.paint(g);
      }

      private static @NotNull Component getRenderer(@NotNull JTree tree, @Nullable TreePath path) {
        return tree.getCellRenderer().getTreeCellRendererComponent(
          tree,
          TreeUtil.getLastUserObject(path),
          false,
          false,
          true,
          tree.getRowForPath(path),
          false
        );
      }
    }

    private static final class MoreFilesRow extends MyDragSource.DragImageRow {
      private final @NotNull JLabel moreLabel;

      MoreFilesRow(JTree tree, int moreItemsCount) {
        moreLabel = new JLabel(IdeBundle.message("label.more.files", moreItemsCount), EmptyIcon.ICON_16, SwingConstants.LEADING);
        moreLabel.setFont(tree.getFont());
        moreLabel.setSize(moreLabel.getPreferredSize());
      }

      @Override
      @NotNull
      Dimension getSize() {
        return moreLabel.getSize();
      }

      @Override
      void paint(@NotNull Graphics2D g) {
        moreLabel.paint(g);
      }
    }
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    return ActionCallback.DONE;
  }

  /**
   * @deprecated temporary API
   */
  @TestOnly
  @Deprecated(forRemoval = true)
  @NotNull
  public Promise<TreePath> promisePathToElement(@NotNull Object element) {
    TreeVisitor visitor = createVisitor(element);
    if (visitor == null || myTree == null) return Promises.rejectedPromise();
    return TreeUtil.promiseVisit(myTree, visitor);
  }

  @ApiStatus.Internal
  public @Nullable AbstractTreeNode<?> getVisibleAndSelectedUserObject() {
    JTree tree = getTree();
    if (tree == null) return null;
    TreePath path = TreeUtil.getSelectedPathIfOne(tree);
    if (path == null) return null;
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return null;
    Rectangle visible = tree.getVisibleRect();
    if (bounds.y < visible.y || bounds.y > visible.y + visible.height - bounds.height) return null;
    return TreeUtil.getLastUserObject(AbstractTreeNode.class, path);
  }

  AsyncProjectViewSupport getAsyncSupport() {
    return null;
  }

  private final DeleteProvider myDeletePSIElementProvider = new ProjectViewDeleteElementProvider() {

    @Override
    protected PsiElement @NotNull [] getSelectedPSIElements(@NotNull DataContext dataContext) {
      Object[] objects = dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
      if (objects == null) return PsiElement.EMPTY_ARRAY;
      return PsiUtilCore.toPsiElementArray(ContainerUtil.flatMap(Arrays.asList(objects), o -> getElementsFromNode(o)));
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
    if (object instanceof AbstractTreeNode<?> node) {
      if (node.getEqualityObject() instanceof SmartPsiElementPointer<?> ptr) {
        return new ProjectViewNodeVisitor(ptr);
      }
      object = node.getValue();
    }
    else if (object instanceof ProjectFileNode) {
      ProjectFileNode node = (ProjectFileNode)object;
      return createVisitor(node.getVirtualFile());
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

  @Nullable
  @ApiStatus.Internal
  public static TreeVisitor createVisitorByPointer(@Nullable SmartPsiElementPointer<PsiElement> pointer, @Nullable VirtualFile file) {
    if (pointer != null) return new ProjectViewNodeVisitor(pointer, file, null);
    if (file != null) return new ProjectViewFileVisitor(file, null);
    LOG.warn("cannot create visitor without element and/or file");
    return null;
  }
}