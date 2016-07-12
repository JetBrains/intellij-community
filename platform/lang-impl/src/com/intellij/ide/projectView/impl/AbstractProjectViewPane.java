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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.nodes.AbstractModuleNode;
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode;
import com.intellij.ide.projectView.impl.nodes.ModuleGroupNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.*;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractProjectViewPane implements DataProvider, Disposable, BusyObject {
  public static final ExtensionPointName<AbstractProjectViewPane> EP_NAME = ExtensionPointName.create("com.intellij.projectViewPane");

  @NotNull
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

  private DnDTarget myDropTarget;
  private DnDSource myDragSource;
  private DnDManager myDndManager;

  private void queueUpdateByProblem() {
    if (Registry.is("projectView.showHierarchyErrors")) {
      if (myTreeBuilder != null) {
        myTreeBuilder.queueUpdate();
      }
    }
  }

  protected AbstractProjectViewPane(@NotNull Project project) {
    myProject = project;
    WolfTheProblemSolver.ProblemListener problemListener = new WolfTheProblemSolver.ProblemListener() {
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
    WolfTheProblemSolver.getInstance(project).addProblemListener(problemListener, this);
    Disposer.register(project, this);
  }

  protected final void fireTreeChangeListener() {
    if (myTreeChangeListener != null) myTreeChangeListener.run();
  }

  public final void setTreeChangeListener(@NotNull Runnable listener) {
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
    if (Comparing.strEqual(mySubId, subId)) return;
    saveExpandedPaths();
    mySubId = subId;
  }

  public boolean isInitiallyVisible() {
    return true;
  }

  public boolean supportsManualOrder() {
    return false;
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

  @Override
  public void dispose() {
    if (myDndManager != null) {
      if (myDropTarget != null) {
        myDndManager.unregisterTarget(myDropTarget, myTree);
        myDropTarget = null;
      }
      if (myDragSource != null) {
        myDndManager.unregisterSource(myDragSource, myTree);
        myDragSource = null;
      }
      myDndManager = null;
    }
    setTreeBuilder(null);
    myTree = null;
    myTreeStructure = null;
  }

  @NotNull
  public abstract ActionCallback updateFromRoot(boolean restoreExpandedPaths);

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public void selectModule(final Module module, final boolean requestFocus) {
    doSelectModuleOrGroup(module, requestFocus);
  }

  private void doSelectModuleOrGroup(final Object toSelect, final boolean requestFocus) {
    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    final Runnable runnable = () -> {
      ProjectView projectView = ProjectView.getInstance(myProject);
      if (requestFocus) {
        projectView.changeView(getId(), getSubId());
      }
      ((BaseProjectTreeBuilder)getTreeBuilder()).selectInWidth(toSelect, requestFocus, node -> node instanceof AbstractModuleNode || node instanceof ModuleGroupNode || node instanceof AbstractProjectNode);
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
        if (userObject != null && ReflectionUtil.isAssignable(nodeClass, userObject.getClass())) {
          result.add((T)userObject);
        }
      }
    }
    return result;
  }

  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
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
    return PsiUtilCore.toPsiElementArray(psiElements);
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

  @Nullable
  protected Module getNodeModule(@Nullable final Object element) {
    if (element instanceof PsiElement) {
      PsiElement psiElement = (PsiElement)element;
      return ModuleUtilCore.findModuleForPsiElement(psiElement);
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
      Object element = getElementFromTreeNode(lastPathComponent);
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
  public Object getElementFromTreeNode(@Nullable final Object treeNode) {
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

  protected void saveExpandedPaths() {
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

  @NotNull
  public PsiDirectory[] getSelectedDirectories() {
    List<PsiDirectory> directories = ContainerUtil.newArrayList();
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
      return directories.toArray(new PsiDirectory[directories.size()]);
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
      final DefaultMutableTreeNode selectedNode = getSelectedNode();
      if (selectedNode != null) {
        return getSelectedDirectoriesInAmbiguousCase(selectedNode);
      }
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  @NotNull
  protected PsiDirectory[] getSelectedDirectoriesInAmbiguousCase(@NotNull final DefaultMutableTreeNode node) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof AbstractModuleNode) {
      final Module module = ((AbstractModuleNode)userObject).getValue();
      if (module != null) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
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
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  // Drag'n'Drop stuff

  @Nullable
  public static PsiElement[] getTransferedPsiElements(Transferable transferable) {
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

   @Nullable
  public static TreeNode[] getTransferedTreeNodes(Transferable transferable) {
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
      myDropTarget = new ProjectViewDropTarget(myTree, new Retriever() {
        @Override
        public PsiElement getPsiElement(@Nullable TreeNode node) {
          return getPSIElement(getElementFromTreeNode(node));
        }

        @Override
        public Module getModule(TreeNode treeNode) {
          return getNodeModule(getElementFromTreeNode(treeNode));
        }
      }, myProject) {
        @Override
        public void cleanUpOnLeave() {
          beforeDnDLeave();
          super.cleanUpOnLeave();
        }

        @Override
        public boolean update(DnDEvent event) {
          beforeDnDUpdate();
          return super.update(event);
        }
      };
      myDragSource = new MyDragSource();
      myDndManager = DnDManager.getInstance();
      myDndManager.registerSource(myDragSource, myTree);
      myDndManager.registerTarget(myDropTarget, myTree);
    }
  }

  protected void beforeDnDUpdate() { }

  protected void beforeDnDLeave() { }

  public void setTreeBuilder(final AbstractTreeBuilder treeBuilder) {
    if (treeBuilder != null) {
      Disposer.register(this, treeBuilder);
// needs refactoring for project view first
//      treeBuilder.setCanYieldUpdate(true);
    }
    myTreeBuilder = treeBuilder;
  }

  private class MyDragSource implements DnDSource {
    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      if ((action.getActionId() & DnDConstants.ACTION_COPY_OR_MOVE) == 0) return false;
      final Object[] elements = getSelectedElements();
      final PsiElement[] psiElements = getSelectedPSIElements();
      DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      return psiElements.length > 0 || canDragElements(elements, dataContext, action.getActionId());
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      final PsiElement[] psiElements = getSelectedPSIElements();
      final TreeNode[] nodes = getSelectedTreeNodes();
      return new DnDDragStartBean(new TransferableWrapper(){

        @Override
        public List<File> asFileList() {
          return PsiCopyPasteManager.asFileList(psiElements);
        }

        @Override
        public TreeNode[] getTreeNodes() {
          return nodes;
        }

        @Override
        public PsiElement[] getPsiElements() {
          return psiElements;
        }
      });
    }

    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      final TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;

      final int count = paths.length;

      final JLabel label = new JLabel(String.format("%s item%s", count, count == 1 ? "" : "s"));
      label.setOpaque(true);
      label.setForeground(myTree.getForeground());
      label.setBackground(myTree.getBackground());
      label.setFont(myTree.getFont());
      label.setSize(label.getPreferredSize());
      final BufferedImage image = UIUtil.createImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2 = (Graphics2D)image.getGraphics();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      label.paint(g2);
      g2.dispose();

      return new Pair<Image, Point>(image, new Point(-image.getWidth(null), -image.getHeight(null)));
    }

    @Override
    public void dragDropEnd() {
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
    }
  }

  private static boolean canDragElements(Object[] elements, DataContext dataContext, int dragAction) {
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
    if (myTreeBuilder == null || myTreeBuilder.isDisposed()) return ActionCallback.REJECTED;
    return myTreeBuilder.getUi().getReady(requestor);
  }
}
