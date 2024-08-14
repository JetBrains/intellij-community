// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.codeInsight.generation.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.ui.tree.TreePathUtil.toTreePathArray;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;

public class MemberChooser<T extends ClassMember> extends DialogWrapper implements UiCompatibleDataProvider {
  protected Tree myTree;
  private DefaultTreeModel myTreeModel;
  protected JComponent[] myOptionControls;
  private JCheckBox myCopyJavadocCheckbox;
  private JCheckBox myInsertOverrideAnnotationCheckbox;
  private final ArrayList<MemberNode> mySelectedNodes = new ArrayList<>();

  private final SortEmAction mySortAction;

  private boolean myAlphabeticallySorted = false;
  private boolean myShowClasses = true;
  protected boolean myAllowEmptySelection;
  private final boolean myAllowMultiSelection;
  private final boolean myIsInsertOverrideVisible;
  private final JComponent myHeaderPanel;

  protected T[] myElements;
  protected Comparator<? super ElementNode> myComparator = new OrderComparator();

  protected final HashMap<MemberNode,ParentNode> myNodeToParentMap = new HashMap<>();
  protected final HashMap<ClassMember, MemberNode> myElementToNodeMap = new HashMap<>();
  protected final ArrayList<ContainerNode> myContainerNodes = new ArrayList<>();

  protected LinkedHashSet<T> mySelectedElements;

  private static final @NonNls String PROP_SORTED = "MemberChooser.sorted";
  private static final @NonNls String PROP_SHOWCLASSES = "MemberChooser.showClasses";
  private static final @NonNls String PROP_COPYJAVADOC = "MemberChooser.copyJavadoc";

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       @NotNull Project project,
                       @Nullable JComponent headerPanel,
                       JComponent[] optionControls) {
    this(allowEmptySelection, allowMultiSelection, project, false, headerPanel, optionControls);
    resetElements(elements);
    init();
  }

  public MemberChooser(T[] elements, boolean allowEmptySelection, boolean allowMultiSelection, @NotNull Project project) {
    this(elements, allowEmptySelection, allowMultiSelection, project, false);
  }

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       @NotNull Project project,
                       boolean isInsertOverrideVisible) {
    this(elements, allowEmptySelection, allowMultiSelection, project, isInsertOverrideVisible, null);
  }

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       @NotNull Project project,
                       boolean isInsertOverrideVisible,
                       @Nullable JComponent headerPanel
                       ) {
    this(allowEmptySelection, allowMultiSelection, project, isInsertOverrideVisible, headerPanel, null);
    resetElements(elements);
    init();
  }

  protected MemberChooser(boolean allowEmptySelection,
                          boolean allowMultiSelection,
                          @NotNull Project project,
                          boolean isInsertOverrideVisible,
                          @Nullable JComponent headerPanel,
                          JComponent @Nullable [] optionControls) {
    super(project, true);
    myAllowEmptySelection = allowEmptySelection;
    myAllowMultiSelection = allowMultiSelection;
    myIsInsertOverrideVisible = isInsertOverrideVisible;
    myHeaderPanel = headerPanel;
    myTree = createTree();
    myOptionControls = optionControls;
    mySortAction = new SortEmAction();
    mySortAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_MASK)), myTree);
  }

  protected void resetElementsWithDefaultComparator(T[] elements) {
    myComparator = myAlphabeticallySorted ? new AlphaComparator() : new OrderComparator();
    resetElements(elements, null, true);
  }

  public void resetElements(T[] elements) {
    resetElements(elements, null, false);
  }

  public void resetElements(T[] elements, final @Nullable Comparator<? super T> sortComparator, final boolean restoreSelectedElements) {
    final List<T> selectedElements  = restoreSelectedElements && mySelectedElements != null ? new ArrayList<>(mySelectedElements) : null;
    myElements = elements;
    if (sortComparator != null) {
      myComparator = new ElementNodeComparatorWrapper<>(sortComparator);
    }
    mySelectedNodes.clear();
    myNodeToParentMap.clear();
    myElementToNodeMap.clear();
    myContainerNodes.clear();

    ApplicationManager.getApplication().runReadAction(() -> {
      myTreeModel = buildModel();
    });

    myTree.setModel(myTreeModel);
    myTree.setRootVisible(false);


    doSort();

    defaultExpandTree();

    //TODO: dmitry batkovich: appcode tests fail
    //restoreTree();

    if (myOptionControls == null) {
      myCopyJavadocCheckbox = new NonFocusableCheckBox(IdeBundle.message("checkbox.copy.javadoc"));
      if (myIsInsertOverrideVisible) {
        myInsertOverrideAnnotationCheckbox = new NonFocusableCheckBox(IdeBundle.message("checkbox.insert.at.override"));
        myOptionControls = new JCheckBox[] {myCopyJavadocCheckbox, myInsertOverrideAnnotationCheckbox};
      }
      else {
        myOptionControls = new JCheckBox[] {myCopyJavadocCheckbox};
      }
    }

    myTree.doLayout();
    setOKActionEnabled(myAllowEmptySelection || myElements != null && myElements.length > 0);

    if (selectedElements != null) {
      selectElements(selectedElements.toArray(ClassMember.EMPTY_ARRAY));
    }
    if (mySelectedElements == null || mySelectedElements.isEmpty()) {
      expandFirst();
    }
  }

  /**
   * should be invoked in read action
   */
  private DefaultTreeModel buildModel() {
    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    final Ref<Integer> count = new Ref<>(0);
    Ref<Map<MemberChooserObject, ParentNode>> mapRef = new Ref<>();
    mapRef.set(FactoryMap.create(key -> {
      ParentNode node = null;
      DefaultMutableTreeNode parentNode1 = rootNode;

      if (supportsNestedContainers() && key instanceof ClassMember) {
        MemberChooserObject parentNodeDelegate = ((ClassMember)key).getParentNodeDelegate();

        if (parentNodeDelegate != null) {
          parentNode1 = mapRef.get().get(parentNodeDelegate);
        }
      }
      if (isContainerNode(key)) {
        final ContainerNode containerNode = new ContainerNode(parentNode1, key, count);
        node = containerNode;
        myContainerNodes.add(containerNode);
      }
      if (node == null) {
        node = new ParentNode(parentNode1, key, count);
      }
      return node;
    }));
    final Map<MemberChooserObject, ParentNode> map = mapRef.get();
    for (T object : myElements) {
      final ParentNode parentNode = map.get(object.getParentNodeDelegate());
      final MemberNode elementNode = createMemberNode(count, object, parentNode);
      myNodeToParentMap.put(elementNode, parentNode);
      myElementToNodeMap.put(object, elementNode);
    }
    return new DefaultTreeModel(rootNode);
  }

  protected MemberNode createMemberNode(Ref<Integer> count, @NotNull T object, ParentNode parentNode) {
    return new MemberNodeImpl(parentNode, object, count);
  }

  protected boolean supportsNestedContainers() {
    return false;
  }

  protected void defaultExpandTree() {
    TreeUtil.expandAll(myTree);
  }

  protected boolean isContainerNode(MemberChooserObject key) {
    return key instanceof PsiElementMemberChooserObject;
  }

  public void selectElements(ClassMember[] elements) {
    ArrayList<TreePath> selectionPaths = new ArrayList<>();
    for (ClassMember element : elements) {
      MemberNode treeNode = myElementToNodeMap.get(element);
      if (treeNode != null) {
        selectionPaths.add(new TreePath(((DefaultMutableTreeNode)treeNode).getPath()));
      }
    }
    final TreePath[] paths = toTreePathArray(selectionPaths);
    myTree.setSelectionPaths(paths);

    if (paths.length > 0) {
      TreeUtil.showRowCentered(myTree, myTree.getRowForPath(paths[0]), true, true);
    }
  }


  @Override
  protected Action @NotNull [] createActions() {
    final List<Action> actions = new ArrayList<>();
    actions.add(getOKAction());
    if (myAllowEmptySelection) {
      actions.add(new SelectNoneAction());
    }
    actions.add(getCancelAction());
    if (getHelpId() != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  protected void customizeOptionsPanel() {
    if (myInsertOverrideAnnotationCheckbox != null && myIsInsertOverrideVisible) {
      myInsertOverrideAnnotationCheckbox.setSelected(isInsertOverrideAnnotationSelected());
    }
    if (myCopyJavadocCheckbox != null) {
      myCopyJavadocCheckbox.setSelected(PropertiesComponent.getInstance().isTrueValue(PROP_COPYJAVADOC));
    }
  }

  protected boolean isInsertOverrideAnnotationSelected() {
    return false;
  }

  @Override
  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    customizeOptionsPanel();
    JPanel optionsPanel = new JPanel(new VerticalFlowLayout());
    for (final JComponent component : myOptionControls) {
      optionsPanel.add(component);
    }

    panel.add(
      optionsPanel,
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                             JBUI.insetsRight(5), 0, 0)
    );

    if (!myAllowEmptySelection && (myElements == null || myElements.length == 0)) {
      setOKActionEnabled(false);
    }
    panel.add(
      super.createSouthPanel(),
      new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTH, GridBagConstraints.NONE,
                             JBInsets.emptyInsets(), 0, 0)
    );
    return panel;
  }

  @Override
  protected JComponent createNorthPanel() {
    return myHeaderPanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    // Toolbar

    DefaultActionGroup group = new DefaultActionGroup();

    fillToolbarActions(group);

    group.addSeparator();

    ExpandAllAction expandAllAction = new ExpandAllAction();
    expandAllAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_EXPAND_ALL), myTree);
    group.add(expandAllAction);

    CollapseAllAction collapseAllAction = new CollapseAllAction();
    collapseAllAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_COLLAPSE_ALL), myTree);
    group.add(collapseAllAction);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("MemberChooser", group, true);
    toolbar.setTargetComponent(myTree);
    panel.add(toolbar.getComponent(), BorderLayout.NORTH);

    // Tree
    expandFirst();
    defaultExpandTree();
    installSpeedSearch();

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(JBUI.size(350, 450));
    panel.add(scrollPane, BorderLayout.CENTER);

    return panel;
  }

  private void expandFirst() {
    if (getRootNode().getChildCount() > 0) {
      myTree.expandRow(0);
      myTree.setSelectionRow(1);
    }
  }

  protected Tree createTree() {
    final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));

    tree.setCellRenderer(getTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.addKeyListener(new TreeKeyListener());
    tree.addTreeSelectionListener(new MyTreeSelectionListener());

    if (!myAllowMultiSelection) {
      tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (tree.getPathForLocation(e.getX(), e.getY()) != null) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(tree);

    TreeUtil.installActions(tree);
    return tree;
  }

  protected TreeCellRenderer getTreeCellRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                        boolean leaf, int row, boolean hasFocus) {
        if (value instanceof ElementNode) {
          ((ElementNode) value).getDelegate().renderTreeNode(this, tree);
        }
      }
    };
  }

  protected @NotNull String convertElementText(@NotNull String originalElementText) {
    return originalElementText;
  }

  protected void installSpeedSearch() {
    final TreeSpeedSearch treeSpeedSearch = TreeSpeedSearch.installOn(myTree, false, path -> {
      final ElementNode lastPathComponent = (ElementNode)path.getLastPathComponent();
      if (lastPathComponent == null) return null;
      String text = lastPathComponent.getDelegate().getText();
      text = convertElementText(text);
      return text;
    });
    treeSpeedSearch.setComparator(getSpeedSearchComparator());
  }

  protected SpeedSearchComparator getSpeedSearchComparator() {
    return new SpeedSearchComparator(false);
  }

  protected void disableAlphabeticalSorting(@NotNull AnActionEvent event) {
    mySortAction.setSelected(event, false);
  }

  protected void onAlphabeticalSortingEnabled(final AnActionEvent event) {
    //do nothing by default
  }

  protected void fillToolbarActions(DefaultActionGroup group) {
    final boolean alphabeticallySorted = PropertiesComponent.getInstance().isTrueValue(PROP_SORTED);
    if (alphabeticallySorted) {
      setSortComparator(new AlphaComparator());
    }
    myAlphabeticallySorted = alphabeticallySorted;
    group.add(mySortAction);

    if (!supportsNestedContainers()) {
      ShowContainersAction showContainersAction = getShowContainersAction();
      showContainersAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_MASK)),
                                                     myTree);
      setShowClasses(PropertiesComponent.getInstance().getBoolean(PROP_SHOWCLASSES, true));
      group.add(showContainersAction);
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.MemberChooser";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  public JComponent[] getOptionControls() {
    return myOptionControls;
  }

  private @Nullable LinkedHashSet<T> getSelectedElementsList() {
    return getExitCode() == OK_EXIT_CODE ? mySelectedElements : null;
  }

  public @Nullable List<T> getSelectedElements() {
    final LinkedHashSet<T> list = getSelectedElementsList();
    return list == null ? null : new ArrayList<>(list);
  }

  public T @Nullable [] getSelectedElements(T[] a) {
    LinkedHashSet<T> list = getSelectedElementsList();
    if (list == null) return null;
    return list.toArray(a);
  }

  protected final boolean areElementsSelected() {
    return mySelectedElements != null && !mySelectedElements.isEmpty();
  }

  public void setCopyJavadocVisible(boolean state) {
    if (myCopyJavadocCheckbox != null) {
      myCopyJavadocCheckbox.setVisible(state);
    }
  }

  public boolean isCopyJavadoc() {
    return myCopyJavadocCheckbox != null && myCopyJavadocCheckbox.isSelected();
  }

  public boolean isInsertOverrideAnnotation() {
    return myIsInsertOverrideVisible && myInsertOverrideAnnotationCheckbox.isSelected();
  }

  private boolean isAlphabeticallySorted() {
    return myAlphabeticallySorted;
  }

  protected void changeSortComparator(final Comparator<? super T> comparator) {
    setSortComparator(new ElementNodeComparatorWrapper<>(comparator));
  }

  private void setSortComparator(Comparator<? super ElementNode> sortComparator) {
    if (myComparator.equals(sortComparator)) return;
    myComparator = sortComparator;
    doSort();
  }

  protected void doSort() {
    Pair<ElementNode, List<ElementNode>> pair = storeSelection();

    Enumeration<TreeNode> children = getRootNodeChildren();
    while (children.hasMoreElements()) {
      ParentNode classNode = (ParentNode)children.nextElement();
      sortNode(classNode, myComparator);
      myTreeModel.nodeStructureChanged(classNode);
    }

    restoreSelection(pair);
  }

  private static void sortNode(ParentNode node, final Comparator<? super ElementNode> sortComparator) {
    ArrayList<ElementNode> arrayList = new ArrayList<>();
    Enumeration<TreeNode> children = node.children();
    while (children.hasMoreElements()) {
      arrayList.add((ElementNode)children.nextElement());
    }

    arrayList.sort(sortComparator);

    replaceChildren(node, arrayList);
  }

  private static void replaceChildren(final DefaultMutableTreeNode node, final Collection<? extends ElementNode> arrayList) {
    node.removeAllChildren();
    for (ElementNode child : arrayList) {
      node.add(child);
    }
  }

  protected void restoreTree() {
    Pair<ElementNode, List<ElementNode>> selection = storeSelection();

    DefaultMutableTreeNode root = getRootNode();
    if (!myShowClasses || myContainerNodes.isEmpty()) {
      List<ParentNode> otherObjects = new ArrayList<>();
      Enumeration<TreeNode> children = getRootNodeChildren();
      ParentNode newRoot = new ParentNode(null, new MemberChooserObjectBase(getAllContainersNodeName()), new Ref<>(0));
      while (children.hasMoreElements()) {
        final ParentNode nextElement = (ParentNode)children.nextElement();
        if (nextElement instanceof ContainerNode containerNode) {
          Enumeration<TreeNode> memberNodes = containerNode.children();
          List<MemberNode> memberNodesList = new ArrayList<>();
          while (memberNodes.hasMoreElements()) {
            memberNodesList.add((MemberNode)memberNodes.nextElement());
          }
          for (MemberNode memberNode : memberNodesList) {
            newRoot.add(memberNode);
          }
        }
        else {
          otherObjects.add(nextElement);
        }
      }
      replaceChildren(root, otherObjects);
      sortNode(newRoot, myComparator);
      if (newRoot.children().hasMoreElements()) root.add(newRoot);
    }
    else {
      Enumeration<TreeNode> children = getRootNodeChildren();
      while (children.hasMoreElements()) {
        ParentNode allClassesNode = (ParentNode)children.nextElement();
        Enumeration<TreeNode> memberNodes = allClassesNode.children();
        ArrayList<MemberNode> arrayList = new ArrayList<>();
        while (memberNodes.hasMoreElements()) {
          arrayList.add((MemberNode)memberNodes.nextElement());
        }
        arrayList.sort(myComparator);
        for (MemberNode memberNode : arrayList) {
          myNodeToParentMap.get(memberNode).add(memberNode);
        }
      }
      replaceChildren(root, myContainerNodes);
    }
    myTreeModel.nodeStructureChanged(root);

    defaultExpandTree();

    restoreSelection(selection);
  }

  protected void setShowClasses(boolean showClasses) {
    myShowClasses = showClasses;
    restoreTree();
  }

  protected @Nls(capitalization = Nls.Capitalization.Sentence) String getAllContainersNodeName() {
    return IdeBundle.message("node.memberchooser.all.classes");
  }

  private Enumeration<TreeNode> getRootNodeChildren() {
    return getRootNode().children();
  }

  protected DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)myTreeModel.getRoot();
  }

  private Pair<ElementNode,List<ElementNode>> storeSelection() {
    List<ElementNode> selectedNodes = new ArrayList<>();
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        selectedNodes.add((ElementNode)path.getLastPathComponent());
      }
    }
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    return Pair.create(leadSelectionPath != null ? (ElementNode)leadSelectionPath.getLastPathComponent() : null, selectedNodes);
  }


  private void restoreSelection(Pair<? extends ElementNode, ? extends List<ElementNode>> pair) {
    List<ElementNode> selectedNodes = pair.second;

    DefaultMutableTreeNode root = getRootNode();

    ArrayList<TreePath> toSelect = new ArrayList<>();
    for (ElementNode node : selectedNodes) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
      if (root.isNodeDescendant(treeNode)) {
        toSelect.add(new TreePath(treeNode.getPath()));
      }
    }

    if (!toSelect.isEmpty()) {
      myTree.setSelectionPaths(toTreePathArray(toSelect));
    }

    ElementNode leadNode = pair.first;
    if (leadNode != null) {
      myTree.setLeadSelectionPath(new TreePath(((DefaultMutableTreeNode)leadNode).getPath()));
    }
  }

  @Override
  public void dispose() {
    PropertiesComponent instance = PropertiesComponent.getInstance();
    instance.setValue(PROP_SORTED, isAlphabeticallySorted());
    instance.setValue(PROP_SHOWCLASSES, myShowClasses);

    if (myCopyJavadocCheckbox != null) {
      instance.setValue(PROP_COPYJAVADOC, Boolean.toString(myCopyJavadocCheckbox.isSelected()));
    }

    final Container contentPane = getContentPane();
    if (contentPane != null) {
      contentPane.removeAll();
    }
    mySelectedNodes.clear();
    myElements = null;
    super.dispose();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (!(ContainerUtil.getFirstItem(mySelectedElements) instanceof ClassMemberWithElement member)) return;
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> member.getElement());
  }

  private final class MyTreeSelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();
      if (paths == null) return;

      boolean reversed = false;
      if (paths.length > 1 &&
          myTree.getRowForPath(paths[0]) < myTree.getRowForPath(e.getOldLeadSelectionPath()) &&
          myTree.getRowForPath(paths[paths.length - 1]) < myTree.getRowForPath(e.getOldLeadSelectionPath())) {
        // `paths` is always sorted from top to bottom (as shown on screen), even when selected in a different order.
        // When the user selects from the bottom to the top, we want to have the paths in that order too.
        // For example members a, b and c.
        // Case 1: c is selected. The user shift-clicks a. Event contains a and b, in that order. We want to reverse,
        // so the order of selection will be c, b, a (not c, a, b).
        // Case 2: b is selected. The user presses cmd/ctrl+a (select all). Event contains root, a, c. No need to
        // reverse. The order of selection will be a, b, c.
        // Case 3: a is selected. The user shift-clicks c. Event contains b and c, in that order. No need to
        // reverse. The order of selection will be a, b, c.
        paths = ArrayUtil.reverseArray(paths);
        reversed = true;
      }
      for (int i = 0; i < paths.length; i++) {
        if (paths[i].getLastPathComponent() instanceof MemberNode memberNode) {
          if (e.isAddedPath(reversed ? paths.length - (i + 1) : i)) {
            if (!mySelectedNodes.contains(memberNode)) {
              mySelectedNodes.add(memberNode);
            }
          }
          else {
            mySelectedNodes.remove(memberNode);
          }
        }
      }
      mySelectedElements = new LinkedHashSet<>();
      for (MemberNode selectedNode : mySelectedNodes) {
        mySelectedElements.add((T)selectedNode.getDelegate());
      }
      if (!myAllowEmptySelection) {
        setOKActionEnabled(!mySelectedElements.isEmpty());
      }
    }
  }

  protected interface ElementNode extends MutableTreeNode {
    @NotNull
    MemberChooserObject getDelegate();
    int getOrder();
  }

  protected interface MemberNode extends ElementNode {}

  protected abstract static class ElementNodeImpl extends DefaultMutableTreeNode implements ElementNode {
    private final int myOrder;
    private final @NotNull MemberChooserObject myDelegate;

    public ElementNodeImpl(@Nullable DefaultMutableTreeNode parent, @NotNull MemberChooserObject delegate, Ref<Integer> order) {
      myOrder = order.get();
      order.set(myOrder + 1);
      myDelegate = delegate;
      if (parent != null) {
        parent.add(this);
      }
    }

    @Override
    public @NotNull MemberChooserObject getDelegate() {
      return myDelegate;
    }

    @Override
    public int getOrder() {
      return myOrder;
    }
  }

  protected static final class MemberNodeImpl extends ElementNodeImpl implements MemberNode {
    public MemberNodeImpl(ParentNode parent, @NotNull ClassMember delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  protected static class ParentNode extends ElementNodeImpl {
    public ParentNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  protected static final class ContainerNode extends ParentNode {
    public ContainerNode(DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  private final class SelectNoneAction extends AbstractAction {
    SelectNoneAction() {
      super(IdeBundle.message("action.select.none"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myTree.clearSelection();
      doOKAction();
    }
  }

  private final class TreeKeyListener extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      TreePath path = myTree.getLeadSelectionPath();
      if (path == null) return;
      final Object lastComponent = path.getLastPathComponent();
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        if (lastComponent instanceof ParentNode) return;
        doOKAction();
        e.consume();
      }
      else if (e.getKeyCode() == KeyEvent.VK_INSERT) {
        if (lastComponent instanceof ElementNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastComponent;
          if (!mySelectedNodes.contains(node)) {
            if (node.getNextNode() != null) {
              myTree.setSelectionPath(new TreePath(node.getNextNode().getPath()));
            }
          }
          else {
            if (node.getNextNode() != null) {
              myTree.removeSelectionPath(new TreePath(node.getPath()));
              myTree.setSelectionPath(new TreePath(node.getNextNode().getPath()));
              myTree.repaint();
            }
          }
          e.consume();
        }
      }
    }
  }

  private final class SortEmAction extends ToggleAction implements DumbAware {
    SortEmAction() {
      super(PlatformEditorBundle.messagePointer("action.sort.alphabetically"), AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return isAlphabeticallySorted();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      myAlphabeticallySorted = flag;
      setSortComparator(flag ? new AlphaComparator() : new OrderComparator());
      if (flag) {
        MemberChooser.this.onAlphabeticalSortingEnabled(event);
      }
    }
  }

  protected ShowContainersAction getShowContainersAction() {
    return new ShowContainersAction(IdeBundle.messagePointer("action.show.classes"),
                                    IconManager.getInstance().getPlatformIcon(PlatformIcons.Class));
  }

  protected final class ShowContainersAction extends ToggleAction implements DumbAware {

    public ShowContainersAction(@NotNull Supplier<@NlsActions.ActionText String> text, final Icon icon) {
      super(text, icon);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return myShowClasses;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      setShowClasses(flag);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myContainerNodes.size() > 1);
    }
  }

  private final class ExpandAllAction extends AnAction implements DumbAware {
    ExpandAllAction() {
      super(IdeBundle.messagePointer("action.expand.all"), AllIcons.Actions.Expandall);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      TreeUtil.expandAll(myTree);
    }
  }

  private final class CollapseAllAction extends AnAction implements DumbAware {
    CollapseAllAction() {
      super(IdeBundle.messagePointer("action.collapse.all"), AllIcons.Actions.Collapseall);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      TreeUtil.collapseAll(myTree, true, 1);
    }
  }

  private static final class AlphaComparator implements Comparator<ElementNode> {
    @Override
    public int compare(ElementNode n1, ElementNode n2) {
      return n1.getDelegate().getText().compareToIgnoreCase(n2.getDelegate().getText());
    }
  }

  protected static final class OrderComparator implements Comparator<ElementNode> {
    public OrderComparator() {
    } // To make this class instantiable from the subclasses

    @Override
    public int compare(ElementNode n1, ElementNode n2) {
      if (n1.getDelegate() instanceof ClassMemberWithElement && n2.getDelegate() instanceof ClassMemberWithElement) {
        PsiElement element1 = ((ClassMemberWithElement)n1.getDelegate()).getElement();
        PsiElement element2 = ((ClassMemberWithElement)n2.getDelegate()).getElement();
        if (!(element1 instanceof PsiCompiledElement) && !(element2 instanceof PsiCompiledElement)) {
          final PsiFile file1 = element1.getContainingFile();
          final PsiFile file2 = element2.getContainingFile();
          if (Comparing.equal(file1, file2)) {
            return element1.getTextOffset() - element2.getTextOffset();
          }
          else {
            if (file2 == null) return -1;
            if (file1 == null) return 1;
            return comparing(PsiFile::getVirtualFile, nullsLast(comparing(VirtualFile::getPath))).compare(file1, file2);
          }
        }
      }
      return n1.getOrder() - n2.getOrder();
    }
  }

  private static final class ElementNodeComparatorWrapper<T> implements Comparator<ElementNode> {
    private final Comparator<? super T> myDelegate;

    ElementNodeComparatorWrapper(final Comparator<? super T> delegate) {
      myDelegate = delegate;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(final ElementNode o1, final ElementNode o2) {
      return myDelegate.compare((T) o1.getDelegate(), (T) o2.getDelegate());
    }
  }
}
