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

/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.nodes.AbstractModuleNode;
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode;
import com.intellij.ide.projectView.impl.nodes.ModuleGroupNode;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.refactoring.actions.MoveAction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public abstract class AbstractProjectViewPane implements DataProvider, Disposable  {
  public static ExtensionPointName<AbstractProjectViewPane> EP_NAME = ExtensionPointName.create("com.intellij.projectViewPane");

  protected final Project myProject;
  private Runnable myTreeChangeListener;
  protected DnDAwareTree myTree;
  protected AbstractTreeStructure myTreeStructure;
  private AbstractTreeBuilder myTreeBuilder;
  // subId->Tree state; key may be null
  private final Map<String,TreeState> myReadTreeState = new HashMap<String, TreeState>();
  private String mySubId;
  @NonNls private static final String ELEMENT_SUBPANE = "subPane";
  @NonNls private static final String ATTRIBUTE_SUBID = "subId";

  protected AbstractProjectViewPane(Project project) {
    myProject = project;
  }

  protected final void fireTreeChangeListener() {
    if (myTreeChangeListener != null) myTreeChangeListener.run();
  }

  public final void setTreeChangeListener(Runnable listener) {
    myTreeChangeListener = listener;
  }

  public final void removeTreeChangeListener() {
    myTreeChangeListener = null;
  }

  public abstract String getTitle();
  public abstract Icon getIcon();
  @NotNull public abstract String getId();
  @Nullable public final String getSubId(){
    return mySubId;
  }

  public final void setSubId(@Nullable String subId) {
    saveExpandedPaths();
    mySubId = subId;
  }

  public boolean isInitiallyVisible() {
    return true;
  }

  /**
   * @return all supported sub views IDs.
   * should return empty array if there is no subViews as in Project/Packages view.
   */
  @NotNull public String[] getSubIds(){
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull public String getPresentableSubIdName(@NotNull final String subId) {
    throw new IllegalStateException("should not call");
  }
  public abstract JComponent createComponent();
  public JComponent getComponentToFocus() {
    return myTree;
  }
  public void expand(@Nullable final Object[] path, final boolean requestFocus){
    if (getTreeBuilder() == null || path == null) return;
    getTreeBuilder().buildNodeForPath(path);

    DefaultMutableTreeNode node = getTreeBuilder().getNodeForPath(path);
    if (node == null) {
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
    if (requestFocus) {
      myTree.requestFocus();
    }
    TreeUtil.selectPath(myTree, treePath);
  }

  public void dispose() {
    setTreeBuilder(null);
    myTree = null;
    myTreeStructure = null;
  }

  public abstract ActionCallback updateFromRoot(boolean restoreExpandedPaths);

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);
  public void selectModule(final Module module, final boolean requestFocus) {
    doSelectModuleOrGroup(module, requestFocus);
  }

  private void doSelectModuleOrGroup(final Object toSelect, final boolean requestFocus) {
    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    final Runnable runnable = new Runnable() {
      public void run() {
        ProjectView projectView = ProjectView.getInstance(myProject);
        if (requestFocus) {
          projectView.changeView(getId(), getSubId());
        }
        ((BaseProjectTreeBuilder)getTreeBuilder()).selectInWidth(toSelect, requestFocus, new Condition<AbstractTreeNode>(){
          public boolean value(final AbstractTreeNode node) {
            return node instanceof AbstractModuleNode || node instanceof ModuleGroupNode || node instanceof AbstractProjectNode;
          }
        });
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  public void selectModuleGroup(ModuleGroup moduleGroup, boolean requestFocus) {
    doSelectModuleOrGroup(moduleGroup, requestFocus);
  }

  public TreePath[] getSelectionPaths() {
    return myTree == null ? null : myTree.getSelectionPaths();
  }

  public void addToolbarActions(DefaultActionGroup actionGroup) {
  }

  @NotNull
  protected <T extends NodeDescriptor> List<T> getSelectedNodes(final Class<T> nodeClass){
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return Collections.emptyList();
    final ArrayList<T> result = new ArrayList<T>();
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object userObject = node.getUserObject();
        if (userObject != null && ReflectionCache.isAssignable(nodeClass, userObject.getClass())) {
          result.add((T)userObject);
        }
      }
    }
    return result;
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;
      final ArrayList<Navigatable> navigatables = new ArrayList<Navigatable>();
      for (TreePath path : paths) {
        Object lastPathComponent = path.getLastPathComponent();
        if (lastPathComponent instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
          Object userObject = node.getUserObject();
          if (userObject instanceof Navigatable) {
            navigatables.add((Navigatable)userObject);
          }
          else if (node instanceof Navigatable) {
            navigatables.add((Navigatable)node);
          }
        }
      }
      if (navigatables.isEmpty()) {
        return null;
      }
      else {
        return navigatables.toArray(new Navigatable[navigatables.size()]);
      }
    }
    if (myTreeStructure instanceof AbstractTreeStructureBase) {
      return ((AbstractTreeStructureBase) myTreeStructure).getDataFromProviders(getSelectedNodes(AbstractTreeNode.class), dataId);
    }
    return null;
  }

  // used for sorting tabs in the tabbed pane
  public abstract int getWeight();

  public abstract SelectInTarget createSelectInTarget();

  public final TreePath getSelectedPath() {
    final TreePath[] paths = getSelectionPaths();
    if (paths != null && paths.length == 1) return paths[0];
    return null;
  }

  public final NodeDescriptor getSelectedDescriptor() {
    final DefaultMutableTreeNode node = getSelectedNode();
    if (node == null) return null;
    Object userObject = node.getUserObject();
    if (userObject instanceof NodeDescriptor) {
      return (NodeDescriptor)userObject;
    }
    return null;
  }

  public final DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectedPath();
    if (path == null) {
      return null;
    }
    Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
      return null;
    }
    return (DefaultMutableTreeNode)lastPathComponent;
  }

  public final Object getSelectedElement() {
    final Object[] elements = getSelectedElements();
    return elements.length == 1 ? elements[0] : null;
  }

  @NotNull
  public final PsiElement[] getSelectedPSIElements() {
    List<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (Object element : getSelectedElements()) {
      final PsiElement psiElement = getPSIElement(element);
      if (psiElement != null) {
        psiElements.add(psiElement);
      }
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  @Nullable
  protected PsiElement getPSIElement(@Nullable final Object element) {
    if (element instanceof PsiElement) {
      PsiElement psiElement = (PsiElement)element;
      if (psiElement.isValid()) {
        return psiElement;
      }
    }
    return null;
  }

  @NotNull
  public final Object[] getSelectedElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    ArrayList<Object> list = new ArrayList<Object>(paths.length);
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof TreeNode) {
        Object element = getElement((TreeNode)lastPathComponent);
        if (element != null) {
          list.add(element);
        }
      }
    }
    return ArrayUtil.toObjectArray(list);
  }

  @Nullable
  private Object getElement(@Nullable final TreeNode treeNode) {
    if (treeNode instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)treeNode;
      return exhumeElementFromNode(node);
    }
    return null;
  }

  private TreeNode[] getSelectedTreeNodes(){
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    final List<TreeNode> result = new ArrayList<TreeNode>();
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        result.add ( (TreeNode) lastPathComponent);
      }
    }
    return result.toArray(new TreeNode[result.size()]);
  }


  protected Object exhumeElementFromNode(final DefaultMutableTreeNode node) {
    return extractUserObject(node);
  }

  public static Object extractUserObject(DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    Object element = null;
    if (userObject instanceof AbstractTreeNode) {
      AbstractTreeNode descriptor = (AbstractTreeNode)userObject;
      element = descriptor.getValue();
    }
    else if (userObject instanceof NodeDescriptor) {
      NodeDescriptor descriptor = (NodeDescriptor)userObject;
      element = descriptor.getElement();
      if (element instanceof AbstractTreeNode) {
        element = ((AbstractTreeNode)element).getValue();
      }
    }
    else if (userObject != null) {
      element = userObject;
    }
    return element;
  }

  public AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public void readExternal(Element element) throws InvalidDataException {
    List<Element> subPanes = element.getChildren(ELEMENT_SUBPANE);
    for (Element subPane : subPanes) {
      String subId = subPane.getAttributeValue(ATTRIBUTE_SUBID);
      TreeState treeState = new TreeState();
      treeState.readExternal(subPane);
      myReadTreeState.put(subId, treeState);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    saveExpandedPaths();
    for (String subId : myReadTreeState.keySet()) {
      TreeState treeState = myReadTreeState.get(subId);
      Element subPane = new Element(ELEMENT_SUBPANE);
      if (subId != null) {
        subPane.setAttribute(ATTRIBUTE_SUBID, subId);
      }
      treeState.writeExternal(subPane);
      element.addContent(subPane);
    }
  }

  void saveExpandedPaths() {
    if (myTree != null) {
      TreeState treeState = TreeState.createOn(myTree);
      myReadTreeState.put(getSubId(), treeState);
    }
  }

  public final void restoreExpandedPaths(){
    TreeState treeState = myReadTreeState.get(getSubId());
    if (treeState != null) {
      treeState.applyTo(myTree);
    }
  }

  public void installComparator() {
    final ProjectView projectView = ProjectView.getInstance(myProject);
    getTreeBuilder().setNodeDescriptorComparator(new GroupByTypeComparator(projectView, getId()));
  }

  public JTree getTree() {
    return myTree;
  }

  public PsiDirectory[] getSelectedDirectories() {
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
          return psiDirectory != null ? new PsiDirectory[]{psiDirectory} : PsiDirectory.EMPTY_ARRAY;
        }
      }
    }
    else {
      final DefaultMutableTreeNode selectedNode = getSelectedNode();
      if (selectedNode != null) {
        return getSelectedDirectoriesInAmbiguousCase(selectedNode);
      }
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  protected PsiDirectory[] getSelectedDirectoriesInAmbiguousCase(@NotNull final DefaultMutableTreeNode node) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof AbstractModuleNode) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(((AbstractModuleNode)userObject).getValue());
      final VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
      List<PsiDirectory> dirs = new ArrayList<PsiDirectory>(sourceRoots.length);
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      for (final VirtualFile sourceRoot : sourceRoots) {
        final PsiDirectory directory = psiManager.findDirectory(sourceRoot);
        if (directory != null) {
          dirs.add(directory);
        }
      }
      return dirs.toArray(new PsiDirectory[dirs.size()]);
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  // Drag'n'Drop stuff

  public static final DataFlavor[] FLAVORS;
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.projectView.ProjectViewImpl");
  private final MyDragSourceListener myDragSourceListener = new MyDragSourceListener();

  static {
    DataFlavor[] flavors;
    try {
      final Class aClass = MyTransferable.class;
      flavors = new DataFlavor[]{new DataFlavor(
                      DataFlavor.javaJVMLocalObjectMimeType + ";class=" + aClass.getName(),
                      FavoritesTreeViewPanel.ABSTRACT_TREE_NODE_TRANSFERABLE,
                      aClass.getClassLoader()
                    )};
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);  // should not happen
      flavors = new DataFlavor[0];
    }
    FLAVORS = flavors;
  }

  protected void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(myTree, DnDConstants.ACTION_COPY_OR_MOVE, new MyDragGestureListener());
      new DropTarget(myTree, new MoveDropTargetListener(new PsiRetriever() {
        @Nullable
        public PsiElement getPsiElement(@Nullable final TreeNode node) {
          return getPSIElement(getElement(node));
        }
      }, myTree, myProject, FLAVORS[0]));

      myTree.enableDnd(this);
    }
  }

  public void setTreeBuilder(final AbstractTreeBuilder treeBuilder) {
    if (treeBuilder != null) {
      Disposer.register(this, treeBuilder);
// needs refactoring for project view first
//      treeBuilder.setCanYieldUpdate(true);
    }
    myTreeBuilder = treeBuilder;
  }

  private static class MyTransferable implements Transferable {
    private final Object myTransferable;

    public MyTransferable(Object transferable) {
      myTransferable = transferable;
    }

    public DataFlavor[] getTransferDataFlavors() {
      DataFlavor[] flavors = new DataFlavor[2];
      flavors [0] = FLAVORS [0];
      flavors [1] = DataFlavor.javaFileListFlavor;
      return flavors;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      DataFlavor[] flavors = getTransferDataFlavors();
      return ArrayUtil.find(flavors, flavor) != -1;
    }

    public Object getTransferData(DataFlavor flavor) {
      if (flavor == DataFlavor.javaFileListFlavor) {
        TransferableWrapper wrapper = (TransferableWrapper) myTransferable;
        return PsiCopyPasteManager.asFileList(wrapper.getPsiElements());
      }
      return myTransferable;
    }
  }

  public interface TransferableWrapper {
    TreeNode[] getTreeNodes();
    @Nullable PsiElement[] getPsiElements();
  }

  private class MyDragGestureListener implements DragGestureListener {
    public void dragGestureRecognized(DragGestureEvent dge) {
      if ((dge.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0) return;
      DataContext dataContext = DataManager.getInstance().getDataContext();
      ProjectView projectView = ProjectViewImpl.DATA_KEY.getData(dataContext);
      if (projectView == null) return;

      final AbstractProjectViewPane currentPane = projectView.getCurrentProjectViewPane();
      final TreeNode[] nodes = currentPane.getSelectedTreeNodes();
      if (nodes != null) {
        final Object[] elements = currentPane.getSelectedElements();
        final PsiElement[] psiElements = currentPane.getSelectedPSIElements();
        try {
          Object transferableWrapper = new TransferableWrapper() {
            public TreeNode[] getTreeNodes() {
              return nodes;
            }
            public PsiElement[] getPsiElements() {
              return psiElements;
            }
          };

          //FavoritesManager.getInstance(myProject).getCurrentTreeViewPanel().setDraggableObject(draggableObject.getClass(), draggableObject.getValue());
          if ((psiElements != null && psiElements.length > 0) || canDragElements(elements, dataContext, dge.getDragAction())) {
            dge.startDrag(DragSource.DefaultMoveNoDrop, new MyTransferable(transferableWrapper), myDragSourceListener);
          }
        }
        catch (InvalidDnDOperationException idoe) {
          // ignore
        }
      }
    }

    private boolean canDragElements(Object[] elements, DataContext dataContext, int dragAction) {
      for (Object element : elements) {
        if (element instanceof Module) {
          return true;
        }
      }
      if (dragAction == DnDConstants.ACTION_MOVE) {
        final MoveAction.MoveProvider provider = MoveAction.MoveProvider.DATA_KEY.getData(dataContext);
        return provider != null && provider.isEnabledOnDataContext(dataContext);
      }
      return false;
    }
  }

  private static class MyDragSourceListener implements DragSourceListener {

    public void dragEnter(DragSourceDragEvent dsde) {
      dsde.getDragSourceContext().setCursor(null);
    }

    public void dragOver(DragSourceDragEvent dsde) {}

    public void dropActionChanged(DragSourceDragEvent dsde) {
      dsde.getDragSourceContext().setCursor(null);
    }

    public void dragDropEnd(DragSourceDropEvent dsde) { }

    public void dragExit(DragSourceEvent dse) { }
  }
}
