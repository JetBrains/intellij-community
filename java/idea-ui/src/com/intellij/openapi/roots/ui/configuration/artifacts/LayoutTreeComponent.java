// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ArtifactRootNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingNodeSource;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingTreeNodeFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

public class LayoutTreeComponent implements DnDTarget, Disposable {
  @NonNls private static final String EMPTY_CARD = "<empty>";
  @NonNls private static final String PROPERTIES_CARD = "properties";
  private final ArtifactEditorImpl myArtifactsEditor;
  private final LayoutTree myTree;
  private final JPanel myTreePanel;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;
  private final ArtifactEditorContext myContext;
  private final Artifact myOriginalArtifact;
  private final StructureTreeModel<LayoutTreeStructure> myStructureTreeModel;
  private SelectedElementInfo<?> mySelectedElementInfo = new SelectedElementInfo<>(null);
  private JPanel myPropertiesPanelWrapper;
  private JPanel myPropertiesPanel;
  private boolean mySortElements;
  private final LayoutTreeStructure myTreeStructure;

  public LayoutTreeComponent(ArtifactEditorImpl artifactsEditor, ComplexElementSubstitutionParameters substitutionParameters,
                             ArtifactEditorContext context, Artifact originalArtifact, boolean sortElements) {
    myArtifactsEditor = artifactsEditor;
    mySubstitutionParameters = substitutionParameters;
    myContext = context;
    myOriginalArtifact = originalArtifact;
    mySortElements = sortElements;
    myTreeStructure = new LayoutTreeStructure();
    myStructureTreeModel = new StructureTreeModel<>(myTreeStructure, getComparator(), this);
    myTree = new LayoutTree(myArtifactsEditor, myStructureTreeModel);
    myTree.setModel(new AsyncTreeModel(myStructureTreeModel, this));
    Disposer.register(this, myTree);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updatePropertiesPanel(false);
      }
    });
    createPropertiesPanel();
    myTreePanel = new JPanel(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myTreePanel.add(myPropertiesPanelWrapper, BorderLayout.SOUTH);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DnDManager.getInstance().registerTarget(this, myTree);
    }
  }

  @Nullable
  private WeightBasedComparator getComparator() {
    return mySortElements ? new WeightBasedComparator(true) : null;
  }

  public void setSortElements(boolean sortElements) {
    mySortElements = sortElements;
    myStructureTreeModel.setComparator(getComparator());
    myArtifactsEditor.getContext().getParent().getDefaultSettings().setSortElements(sortElements);
  }

  @Nullable
  private static PackagingElementNode getNode(Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    return userObject instanceof PackagingElementNode ? (PackagingElementNode)userObject : null;
  }

  private void createPropertiesPanel() {
    myPropertiesPanel = new JPanel(new BorderLayout());
    final JPanel emptyPanel = new JPanel();
    emptyPanel.setMinimumSize(JBUI.emptySize());
    emptyPanel.setPreferredSize(JBUI.emptySize());

    myPropertiesPanelWrapper = new JPanel(new CardLayout());
    myPropertiesPanel.setBorder(new CustomLineBorder(1, 0, 0, 0));
    myPropertiesPanelWrapper.add(EMPTY_CARD, emptyPanel);
    myPropertiesPanelWrapper.add(PROPERTIES_CARD, myPropertiesPanel);
  }

  public Artifact getArtifact() {
    return myArtifactsEditor.getArtifact();
  }

  public LayoutTree getLayoutTree() {
    return myTree;
  }

  public void updatePropertiesPanel(final boolean force) {
    final PackagingElement<?> selected = getSelection().getElementIfSingle();
    if (!force && Comparing.equal(selected, mySelectedElementInfo.myElement)) {
      return;
    }
    mySelectedElementInfo.save();
    mySelectedElementInfo = new SelectedElementInfo<PackagingElement<?>>(selected);
    mySelectedElementInfo.showPropertiesPanel();
  }

  public void saveElementProperties() {
    mySelectedElementInfo.save();
  }

  public void rebuildTree() {
    myTreeStructure.clearCaches();
    myStructureTreeModel.invalidate();
    updatePropertiesPanel(true);
    myArtifactsEditor.queueValidation();
  }

  public LayoutTreeSelection getSelection() {
    return myTree.getSelection();
  }

  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    PackagingElementNode<?> parentNode = getParentNode(myTree.getSelection());
    final PackagingElement<?> element = parentNode.getFirstElement();
    final CompositePackagingElement<?> parent;
    if (element instanceof CompositePackagingElement<?>) {
      parent = (CompositePackagingElement<?>)element;
    }
    else {
      parent = getArtifact().getRootElement();
      parentNode = getRootNode();
    }
    if (!checkCanAdd(parent, parentNode)) return;

    final List<? extends PackagingElement<?>> children = type.chooseAndCreate(myContext, getArtifact(), parent);
    final PackagingElementNode<?> finalParentNode = parentNode;
    editLayout(() -> {
      CompositePackagingElement<?> actualParent = getOrCreateModifiableParent(parent, finalParentNode);
      for (PackagingElement<?> child : children) {
        actualParent.addOrFindChild(child);
      }
    });
    updateAndSelect(parentNode, children);
  }

  private static CompositePackagingElement<?> getOrCreateModifiableParent(CompositePackagingElement<?> parentElement, PackagingElementNode<?> node) {
    PackagingElementNode<?> current = node;
    List<String> dirNames = new ArrayList<>();
    while (current != null && !(current instanceof ArtifactRootNode)) {
      final PackagingElement<?> packagingElement = current.getFirstElement();
      if (!(packagingElement instanceof DirectoryPackagingElement)) {
        return parentElement;
      }
      dirNames.add(((DirectoryPackagingElement)packagingElement).getDirectoryName());
      current = current.getParentNode();
    }

    if (current == null) return parentElement;
    final PackagingElement<?> rootElement = current.getElementIfSingle();
    if (!(rootElement instanceof CompositePackagingElement<?>)) return parentElement;

    Collections.reverse(dirNames);
    String path = StringUtil.join(dirNames, "/");
    return PackagingElementFactory.getInstance().getOrCreateDirectory((CompositePackagingElement<?>)rootElement, path);
  }

  public boolean checkCanModify(@NotNull PackagingElement<?> element, @NotNull PackagingElementNode<?> node) {
    return checkCanModify(node.getNodeSource(element));
  }

  public boolean checkCanModifyChildren(@NotNull PackagingElement<?> parentElement,
                                        @NotNull PackagingElementNode<?> parentNode,
                                        @NotNull Collection<? extends PackagingElementNode<?>> children) {
    final List<PackagingNodeSource> sources = new ArrayList<>(parentNode.getNodeSource(parentElement));
    for (PackagingElementNode<?> child : children) {
      sources.addAll(child.getNodeSources());
    }
    return checkCanModify(sources);
  }

  public boolean checkCanModify(final Collection<? extends PackagingNodeSource> nodeSources) {
    if (nodeSources.isEmpty()) {
      return true;
    }

    if (nodeSources.size() > 1) {
      Messages.showErrorDialog(myArtifactsEditor.getMainComponent(),
                               JavaUiBundle.message(
                                 "error.message.the.selected.node.consist.of.several.elements.so.it.cannot.be.edited"));
    }
    else {
    final PackagingNodeSource source = ContainerUtil.getFirstItem(nodeSources, null);
      if (source != null) {
        Messages.showErrorDialog(myArtifactsEditor.getMainComponent(),
                                 JavaUiBundle.message(
                                   "error.message.the.selected.node.belongs.to.0.element.so.it.cannot.be.edited",
                                   source.getPresentableName()));
      }
    }
    return false;

  }

  public boolean checkCanAdd(CompositePackagingElement<?> parentElement, PackagingElementNode<?> parentNode) {
    boolean allParentsAreDirectories = true;
    PackagingElementNode<?> current = parentNode;
    while (current != null && !(current instanceof ArtifactRootNode)) {
      final PackagingElement<?> element = current.getFirstElement();
      if (!(element instanceof DirectoryPackagingElement)) {
        allParentsAreDirectories = false;
        break;
      }
      current = current.getParentNode();
    }

    return allParentsAreDirectories || checkCanModify(parentElement, parentNode);
  }

  public boolean checkCanRemove(final List<? extends PackagingElementNode<?>> nodes) {
    Set<PackagingNodeSource> rootSources = new HashSet<>();
    for (PackagingElementNode<?> node : nodes) {
      rootSources.addAll(getRootNodeSources(node.getNodeSources()));
    }

    if (!rootSources.isEmpty()) {
      final String message;
      if (rootSources.size() == 1) {
        final String name = rootSources.iterator().next().getPresentableName();
        message = "The selected node belongs to '" + name + "' element. Do you want to remove the whole '" + name + "' element from the artifact?";
      }
      else {
        message = "The selected node belongs to " + nodes.size() + " elements. Do you want to remove all these elements from the artifact?";
      }
      final int answer = Messages.showYesNoDialog(myArtifactsEditor.getMainComponent(), message, JavaUiBundle.message(
        "dialog.title.remove.elements"), null);
      if (answer != Messages.YES) return false;
    }
    return true;
  }

  public void updateAndSelect(PackagingElementNode<?> node, final List<? extends PackagingElement<?>> toSelect) {
    myArtifactsEditor.queueValidation();
    myTreeStructure.clearCaches();
    List<PackagingElementNode<?>> nodesToSelect = Collections.synchronizedList(new ArrayList<>(toSelect.size()));
    myStructureTreeModel.invalidate(node, true)
      .thenAsync(result -> TreeUtil.promiseVisit(myTree, (path) -> {
        Object nodeObject = TreeUtil.getLastUserObject(path);
        if (nodeObject instanceof PackagingElementNode && ContainerUtil.intersects(((PackagingElementNode<?>)nodeObject).getPackagingElements(), toSelect)) {
          nodesToSelect.add((PackagingElementNode)nodeObject);
        }
        return TreeVisitor.Action.CONTINUE;
      }))
      .thenAsync(result -> Promises.collectResults(ContainerUtil.map(nodesToSelect, nodeToSelect -> myStructureTreeModel.promiseVisitor(nodeToSelect))))
      .thenAsync(visitors -> TreeUtil.promiseSelect(myTree, visitors.stream()));
  }

  public Promise<TreePath> selectNode(@NotNull String parentPath, @NotNull PackagingElement<?> element) {
    Predicate<PackagingElementNode<?>> filter = node -> node.getPackagingElements().stream().anyMatch(element::isEqualTo);
    return TreeUtil.promiseSelect(myTree, myTree.createVisitorCompositeNodeChild(parentPath, filter));
  }

  @TestOnly
  public Promise<TreePath> selectNode(@NotNull String parentPath, @NotNull String nodeName) {
    Predicate<PackagingElementNode<?>> filter = node -> node.getElementPresentation().getSearchName().equals(nodeName);
    return TreeUtil.promiseSelect(myTree, myTree.createVisitorCompositeNodeChild(parentPath, filter));
  }

  public void editLayout(Runnable action) {
    myContext.editLayout(myOriginalArtifact, action);
  }

  public void removeSelectedElements() {
    final LayoutTreeSelection selection = myTree.getSelection();
    if (!checkCanRemove(selection.getNodes())) return;

    editLayout(() -> removeNodes(selection.getNodes()));

    myArtifactsEditor.rebuildTries();
  }

  public void removeNodes(final List<? extends PackagingElementNode<?>> nodes) {
    Set<PackagingElementNode<?>> parents = new HashSet<>();
    for (PackagingElementNode<?> node : nodes) {
      final List<? extends PackagingElement<?>> toDelete = node.getPackagingElements();
      for (PackagingElement<?> element : toDelete) {
        final Collection<PackagingNodeSource> nodeSources = node.getNodeSource(element);
        if (nodeSources.isEmpty()) {
          final CompositePackagingElement<?> parent = node.getParentElement(element);
          if (parent != null) {
            ContainerUtil.addIfNotNull(parents, node.getParentNode());
            parent.removeChild(element);
          }
        }
        else {
          Collection<PackagingNodeSource> rootSources = getRootNodeSources(nodeSources);
          for (PackagingNodeSource source : rootSources) {
            parents.add(source.getSourceParentNode());
            source.getSourceParentElement().removeChild(source.getSourceElement());
          }
        }
      }
    }
    for (PackagingElementNode<?> parent : parents) {
      myTree.addSubtreeToUpdate(parent);
    }
  }

  private static Collection<PackagingNodeSource> getRootNodeSources(Collection<? extends PackagingNodeSource> nodeSources) {
    Set<PackagingNodeSource> result = new HashSet<>();
    collectRootNodeSources(nodeSources, result);
    return result;
  }

  private static void collectRootNodeSources(Collection<? extends PackagingNodeSource> nodeSources, Set<? super PackagingNodeSource> result) {
    for (PackagingNodeSource nodeSource : nodeSources) {
      final Collection<PackagingNodeSource> parentSources = nodeSource.getParentSources();
      if (parentSources.isEmpty()) {
        result.add(nodeSource);
      }
      else {
        collectRootNodeSources(parentSources, result);
      }
    }
  }

  private PackagingElementNode<?> getParentNode(final LayoutTreeSelection selection) {
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node != null) {
      if (node.getFirstElement() instanceof CompositePackagingElement) {
        return node;
      }
      final PackagingElementNode<?> parent = node.getParentNode();
      if (parent != null) {
        return parent;
      }
    }
    return getRootNode();
  }

  public JPanel getTreePanel() {
    return myTreePanel;
  }

  @Override
  public void dispose() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DnDManager.getInstance().unregisterTarget(this, myTree);
    }
  }

  @Override
  public boolean update(DnDEvent aEvent) {
    aEvent.setDropPossible(false);
    aEvent.hideHighlighter();
    final Object object = aEvent.getAttachedObject();
    if (object instanceof PackagingElementDraggingObject) {
      final DefaultMutableTreeNode parent = findParentCompositeElementNode(aEvent.getRelativePoint().getPoint(myTree));
      if (parent != null) {
        final PackagingElementDraggingObject draggingObject = (PackagingElementDraggingObject)object;
        final PackagingElementNode node = getNode(parent);
        if (node != null && draggingObject.canDropInto(node)) {
          final PackagingElement element = node.getFirstElement();
          if (element instanceof CompositePackagingElement) {
            draggingObject.setTargetNode(node);
            draggingObject.setTargetElement((CompositePackagingElement<?>)element);
            final Rectangle bounds = myTree.getPathBounds(TreeUtil.getPathFromRoot(parent));
            aEvent.setHighlighting(new RelativeRectangle(myTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE);
            aEvent.setDropPossible(true);
          }
        }
      }
    }
    return false;
  }

  @Override
  public void drop(DnDEvent aEvent) {
    final Object object = aEvent.getAttachedObject();
    if (object instanceof PackagingElementDraggingObject) {
      final PackagingElementDraggingObject draggingObject = (PackagingElementDraggingObject)object;
      final PackagingElementNode<?> targetNode = draggingObject.getTargetNode();
      final CompositePackagingElement<?> targetElement = draggingObject.getTargetElement();
      if (targetElement == null || targetNode == null || !draggingObject.checkCanDrop()) return;
      if (!checkCanAdd(targetElement, targetNode)) {
        return;
      }
      final List<PackagingElement<?>> toSelect = new ArrayList<>();
      editLayout(() -> {
        draggingObject.beforeDrop();
        final CompositePackagingElement<?> parent = getOrCreateModifiableParent(targetElement, targetNode);
        for (PackagingElement<?> element : draggingObject.createPackagingElements(myContext)) {
          toSelect.add(element);
          parent.addOrFindChild(element);
        }
      });
      updateAndSelect(targetNode, toSelect);
      myArtifactsEditor.getSourceItemsTree().rebuildTree();
    }
  }

  @Nullable
  private DefaultMutableTreeNode findParentCompositeElementNode(Point point) {
    TreePath path = myTree.getPathForLocation(point.x, point.y);
    while (path != null) {
      final PackagingElement<?> element = myTree.getElementByPath(path);
      if (element instanceof CompositePackagingElement) {
        return (DefaultMutableTreeNode)path.getLastPathComponent();
      }
      path = path.getParentPath();
    }
    return null;
  }

  public void startRenaming(TreePath path) {
    myTree.startEditingAtPath(path);
  }

  public boolean isEditing() {
    return myTree.isEditing();
  }

  public void setRootElement(CompositePackagingElement<?> rootElement) {
    myContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact).setRootElement(rootElement);
    myTreeStructure.updateRootElement();
    rebuildTree();
    myArtifactsEditor.getSourceItemsTree().rebuildTree();
  }

  public PackagingElementNode<?> getRootNode() {
    return myTreeStructure.getRootNode();
  }

  @NotNull
  public CompositePackagingElement<?> getRootElement() {
    return myContext.getRootElement(myOriginalArtifact);
  }

  public void updateTreeNodesPresentation() {
    myStructureTreeModel.invalidate();
  }

  public void updateRootNode() {
    myStructureTreeModel.invalidate(myTreeStructure.getRootElement(), false);
  }

  public void initTree() {
    mySelectedElementInfo.showPropertiesPanel();
  }

  public void putIntoDefaultLocations(@NotNull final List<? extends PackagingSourceItem> items) {
    final List<PackagingElement<?>> toSelect = new ArrayList<>();
    editLayout(() -> {
      final CompositePackagingElement<?> rootElement = getArtifact().getRootElement();
      final ArtifactType artifactType = getArtifact().getArtifactType();
      for (PackagingSourceItem item : items) {
        final String path = artifactType.getDefaultPathFor(item);
        if (path != null) {
          final CompositePackagingElement<?> parent;
          if (path.endsWith(URLUtil.JAR_SEPARATOR)) {
            parent = PackagingElementFactory.getInstance().getOrCreateArchive(rootElement, StringUtil.trimEnd(path, URLUtil.JAR_SEPARATOR));
          }
          else {
            parent = PackagingElementFactory.getInstance().getOrCreateDirectory(rootElement, path);
          }
          final List<? extends PackagingElement<?>> elements = item.createElements(myContext);
          toSelect.addAll(parent.addOrFindChildren(elements));
        }
      }
    });

    myArtifactsEditor.getSourceItemsTree().rebuildTree();
    updateAndSelect(getRootNode(), toSelect);
  }

  public void putElements(@NotNull final String path, @NotNull final List<? extends PackagingElement<?>> elements) {
    final List<PackagingElement<?>> toSelect = new ArrayList<>();
    editLayout(() -> {
      final CompositePackagingElement<?> directory =
        PackagingElementFactory.getInstance().getOrCreateDirectory(getArtifact().getRootElement(), path);
      toSelect.addAll(directory.addOrFindChildren(elements));
    });
    myArtifactsEditor.getSourceItemsTree().rebuildTree();
    updateAndSelect(getRootNode(), toSelect);
  }

  public void packInto(@NotNull final List<? extends PackagingSourceItem> items, final String pathToJar) {
    final List<PackagingElement<?>> toSelect = new ArrayList<>();
    final CompositePackagingElement<?> rootElement = getArtifact().getRootElement();
    editLayout(() -> {
      final CompositePackagingElement<?> archive = PackagingElementFactory.getInstance().getOrCreateArchive(rootElement, pathToJar);
      for (PackagingSourceItem item : items) {
        final List<? extends PackagingElement<?>> elements = item.createElements(myContext);
        archive.addOrFindChildren(elements);
      }
      toSelect.add(archive);
    });

    myArtifactsEditor.getSourceItemsTree().rebuildTree();
    updateAndSelect(getRootNode(), toSelect);
  }

  public boolean isPropertiesModified() {
    final PackagingElementPropertiesPanel panel = mySelectedElementInfo.myCurrentPanel;
    return panel != null && panel.isModified();
  }

  public void resetElementProperties() {
    final PackagingElementPropertiesPanel panel = mySelectedElementInfo.myCurrentPanel;
    if (panel != null) {
      panel.reset();
    }
  }

  public boolean isSortElements() {
    return mySortElements;
  }

  private final class SelectedElementInfo<E extends PackagingElement<?>> {
    private final E myElement;
    private PackagingElementPropertiesPanel myCurrentPanel;

    private SelectedElementInfo(@Nullable E element) {
      myElement = element;
      if (myElement != null) {
        //noinspection unchecked
        myCurrentPanel = element.getType().createElementPropertiesPanel(myElement, myContext);
        myPropertiesPanel.removeAll();
        if (myCurrentPanel != null) {
          myPropertiesPanel.add(BorderLayout.CENTER, ScrollPaneFactory.createScrollPane(myCurrentPanel.createComponent(), true));
          myCurrentPanel.reset();
          myPropertiesPanel.revalidate();
        }
      }
    }

    public void save() {
      if (myCurrentPanel != null && myCurrentPanel.isModified()) {
        editLayout(() -> myCurrentPanel.apply());
      }
    }

    public void showPropertiesPanel() {
      final CardLayout cardLayout = (CardLayout)myPropertiesPanelWrapper.getLayout();
      if (myCurrentPanel != null) {
        cardLayout.show(myPropertiesPanelWrapper, PROPERTIES_CARD);
      }
      else {
        cardLayout.show(myPropertiesPanelWrapper, EMPTY_CARD);
      }
      myPropertiesPanelWrapper.repaint();
    }
  }

  private class LayoutTreeStructure extends SimpleTreeStructure {
    private ArtifactRootNode myRootNode;

    @NotNull
    @Override
    public Object getRootElement() {
      return getRootNode();
    }

    @NotNull
    ArtifactRootNode getRootNode() {
      if (myRootNode == null) {
        myRootNode = PackagingTreeNodeFactory.createRootNode(LayoutTreeComponent.this.getRootElement(), myContext, mySubstitutionParameters, getArtifact().getArtifactType());
      }
      return myRootNode;
    }

    public void updateRootElement() {
      myRootNode = null;
    }
  }
}
