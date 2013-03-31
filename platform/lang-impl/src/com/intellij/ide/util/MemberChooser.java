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

package com.intellij.ide.util;

import com.intellij.codeInsight.generation.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class MemberChooser<T extends ClassMember> extends DialogWrapper implements TypeSafeDataProvider {
  protected Tree myTree;
  private DefaultTreeModel myTreeModel;
  protected JComponent[] myOptionControls;
  private JCheckBox myCopyJavadocCheckbox;
  private JCheckBox myInsertOverrideAnnotationCheckbox;

  private final ArrayList<MemberNode> mySelectedNodes = new ArrayList<MemberNode>();

  private boolean mySorted = false;
  private boolean myShowClasses = true;
  protected boolean myAllowEmptySelection = false;
  private boolean myAllowMultiSelection;
  private final Project myProject;
  private final boolean myIsInsertOverrideVisible;
  private final JComponent myHeaderPanel;

  protected T[] myElements;
  protected final HashMap<MemberNode,ParentNode> myNodeToParentMap = new HashMap<MemberNode, ParentNode>();
  protected final HashMap<ClassMember, MemberNode> myElementToNodeMap = new HashMap<ClassMember, MemberNode>();
  protected final ArrayList<ContainerNode> myContainerNodes = new ArrayList<ContainerNode>();
  protected LinkedHashSet<T> mySelectedElements;

  @NonNls private static final String PROP_SORTED = "MemberChooser.sorted";
  @NonNls private static final String PROP_SHOWCLASSES = "MemberChooser.showClasses";
  @NonNls private static final String PROP_COPYJAVADOC = "MemberChooser.copyJavadoc";

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       @NotNull Project project) {
    this(elements, allowEmptySelection, allowMultiSelection, project, false);
  }

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       @NotNull Project project,
                       @Nullable JComponent headerPanel,
                       JComponent[] optionControls) {
    this(elements, allowEmptySelection, allowMultiSelection, project, false, headerPanel, optionControls);
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
                       JComponent headerPanel
                       ) {
    this(elements, allowEmptySelection, allowMultiSelection, project, isInsertOverrideVisible, headerPanel, null);
  }

  private MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       @NotNull Project project,
                       boolean isInsertOverrideVisible,
                       JComponent headerPanel,
                       @Nullable JComponent[] optionControls
                       ) {
    super(project, true);
    myAllowEmptySelection = allowEmptySelection;
    myAllowMultiSelection = allowMultiSelection;
    myProject = project;
    myIsInsertOverrideVisible = isInsertOverrideVisible;
    myHeaderPanel = headerPanel;
    myTree = createTree();
    myOptionControls = optionControls;
    resetElements(elements);
    init();
  }

  public void resetElements(T[] elements) {
    myElements = elements;
    mySelectedNodes.clear();
    myNodeToParentMap.clear();
    myElementToNodeMap.clear();
    myContainerNodes.clear();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myTreeModel = buildModel();
      }
    });

    myTree.setModel(myTreeModel);
    myTree.setRootVisible(false);

    doSort();

    defaultExpandTree();

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
    setOKActionEnabled(myElements != null && myElements.length > 0);
  }

  /**
   * should be invoked in read action
   */
  private DefaultTreeModel buildModel() {
    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    final Ref<Integer> count = new Ref<Integer>(0);
    final FactoryMap<MemberChooserObject,ParentNode> map = new FactoryMap<MemberChooserObject,ParentNode>() {
      protected ParentNode create(final MemberChooserObject key) {
        ParentNode node = null;
        DefaultMutableTreeNode parentNode = rootNode;

        if (supportsNestedContainers() && key instanceof ClassMember) {
          MemberChooserObject parentNodeDelegate = ((ClassMember)key).getParentNodeDelegate();

          if (parentNodeDelegate != null) {
            parentNode = get(parentNodeDelegate);
          }
        }
        if (isContainerNode(key)) {
            final ContainerNode containerNode = new ContainerNode(parentNode, key, count);
            node = containerNode;
            myContainerNodes.add(containerNode);
        }
        if (node == null) {
          node = new ParentNode(parentNode, key, count);
        }
        return node;
      }
    };

    for (T object : myElements) {
      final ParentNode parentNode = map.get(object.getParentNodeDelegate());
      final MemberNode elementNode = createMemberNode(count, object, parentNode);
      myNodeToParentMap.put(elementNode, parentNode);
      myElementToNodeMap.put(object, elementNode);
    }
    return new DefaultTreeModel(rootNode);
  }

  protected MemberNode createMemberNode(Ref<Integer> count, T object, ParentNode parentNode) {
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
    ArrayList<TreePath> selectionPaths = new ArrayList<TreePath>();
    for (ClassMember element : elements) {
      MemberNode treeNode = myElementToNodeMap.get(element);
      if (treeNode != null) {
        selectionPaths.add(new TreePath(((DefaultMutableTreeNode)treeNode).getPath()));
      }
    }
    myTree.setSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
  }


  @NotNull
  protected Action[] createActions() {
    if (myAllowEmptySelection) {
      return new Action[]{getOKAction(), new SelectNoneAction(), getCancelAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  protected void doHelpAction() {
  }

  protected void customizeOptionsPanel() {
    if (myInsertOverrideAnnotationCheckbox != null && myIsInsertOverrideVisible) {
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();
      myInsertOverrideAnnotationCheckbox.setSelected(styleSettings.INSERT_OVERRIDE_ANNOTATION);
    }
    if (myCopyJavadocCheckbox != null) {
      myCopyJavadocCheckbox.setSelected(PropertiesComponent.getInstance().isTrueValue(PROP_COPYJAVADOC));
    }
  }

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
                             new Insets(0, 0, 0, 5), 0, 0)
    );

    if (myElements == null || myElements.length == 0) {
      setOKActionEnabled(false);
    }
    panel.add(
      super.createSouthPanel(),
      new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTH, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 0), 0, 0)
    );
    return panel;
  }

  @Override
  protected JComponent createNorthPanel() {
    return myHeaderPanel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    // Toolbar

    DefaultActionGroup group = new DefaultActionGroup();

    fillToolbarActions(group);

    group.addSeparator();

    ExpandAllAction expandAllAction = new ExpandAllAction();
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myTree);
    group.add(expandAllAction);

    CollapseAllAction collapseAllAction = new CollapseAllAction();
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myTree);
    group.add(collapseAllAction);

    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(),
              BorderLayout.NORTH);

    // Tree

    myTree.setCellRenderer(getTreeCellRenderer());
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.addKeyListener(new TreeKeyListener());
    myTree.addTreeSelectionListener(new MyTreeSelectionListener());

    if (!myAllowMultiSelection) {
      myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    if (getRootNode().getChildCount() > 0) {
      myTree.expandRow(0);
      myTree.setSelectionRow(1);
    }
    defaultExpandTree();
    installSpeedSearch();

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (myTree.getPathForLocation(e.getX(), e.getY()) != null) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    TreeUtil.installActions(myTree);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(350, 450));
    panel.add(scrollPane, BorderLayout.CENTER);

    return panel;
  }

  protected Tree createTree() {
    return new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }

  protected TreeCellRenderer getTreeCellRenderer() {
    return new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                        boolean leaf, int row, boolean hasFocus) {
        if (value instanceof ElementNode) {
          ((ElementNode) value).getDelegate().renderTreeNode(this, tree);
        }
      }
    };
  }

  protected void installSpeedSearch() {
    final TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Nullable
      public String convert(TreePath path) {
        final ElementNode lastPathComponent = (ElementNode)path.getLastPathComponent();
        if (lastPathComponent == null) return null;
        String text = lastPathComponent.getDelegate().getText();
        if (text != null) {
          int i = text.indexOf(':');
          if (i >= 0) {
            text = text.substring(0, i);
          }
          i = text.indexOf('(');
          if (i >= 0) {
            text = text.substring(0, i);
          }
        }
        return text;
      }
    });
    treeSpeedSearch.setComparator(getSpeedSearchComparator());

    treeSpeedSearch.addChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        myTree.repaint(); // to update match highlighting
      }
    });
  }

  protected SpeedSearchComparator getSpeedSearchComparator() {
    return new SpeedSearchComparator(false);
  }

  protected void fillToolbarActions(DefaultActionGroup group) {
    SortEmAction sortAction = new SortEmAction();
    sortAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_MASK)), myTree);
    setSorted(PropertiesComponent.getInstance().isTrueValue(PROP_SORTED));
    group.add(sortAction);

    if (!supportsNestedContainers()) {
      ShowContainersAction showContainersAction = getShowContainersAction();
      showContainersAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_MASK)), myTree);
      setShowClasses(PropertiesComponent.getInstance().isTrueValue(PROP_SHOWCLASSES));
      group.add(showContainersAction);
    }
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.MemberChooser";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  public JComponent[] getOptionControls() {
    return myOptionControls;
  }

  @Nullable
  private LinkedHashSet<T> getSelectedElementsList() {
    return getExitCode() == OK_EXIT_CODE ? mySelectedElements : null;
  }

  @Nullable
  public List<T> getSelectedElements() {
    final LinkedHashSet<T> list = getSelectedElementsList();
    return list == null ? null : new ArrayList<T>(list);
  }

  @Nullable
  public T[] getSelectedElements(T[] a) {
    LinkedHashSet<T> list = getSelectedElementsList();
    if (list == null) return null;
    return list.toArray(a);
  }

  protected final boolean areElementsSelected() {
    return mySelectedElements != null && !mySelectedElements.isEmpty();
  }

  public void setCopyJavadocVisible(boolean state) {
    myCopyJavadocCheckbox.setVisible(state);
  }

  public boolean isCopyJavadoc() {
    return myCopyJavadocCheckbox.isSelected();
  }

  public boolean isInsertOverrideAnnotation () {
    return myIsInsertOverrideVisible && myInsertOverrideAnnotationCheckbox.isSelected();
  }

  private boolean isSorted() {
    return mySorted;
  }

  private void setSorted(boolean sorted) {
    if (mySorted == sorted) return;
    mySorted = sorted;
    doSort();
  }

  private void doSort() {
    Pair<ElementNode,List<ElementNode>> pair = storeSelection();

    Enumeration<ParentNode> children = getRootNodeChildren();
    while (children.hasMoreElements()) {
      ParentNode classNode = children.nextElement();
      sortNode(classNode, mySorted);
      myTreeModel.nodeStructureChanged(classNode);
    }

    restoreSelection(pair);
  }

  private static void sortNode(ParentNode node, boolean sorted) {
    ArrayList<MemberNode> arrayList = new ArrayList<MemberNode>();
    Enumeration<MemberNode> children = node.children();
    while (children.hasMoreElements()) {
      arrayList.add(children.nextElement());
    }

    Collections.sort(arrayList, sorted ? new AlphaComparator() : new OrderComparator());

    replaceChildren(node, arrayList);
  }

  private static void replaceChildren(final DefaultMutableTreeNode node, final Collection<? extends ElementNode> arrayList) {
    node.removeAllChildren();
    for (ElementNode child : arrayList) {
      node.add(child);
    }
  }

  private void setShowClasses(boolean showClasses) {
    myShowClasses = showClasses;

    Pair<ElementNode,List<ElementNode>> selection = storeSelection();

    DefaultMutableTreeNode root = getRootNode();
    if (!myShowClasses || myContainerNodes.isEmpty()) {
      List<ParentNode> otherObjects = new ArrayList<ParentNode>();
      Enumeration<ParentNode> children = getRootNodeChildren();
      ParentNode newRoot = new ParentNode(null, new MemberChooserObjectBase(getAllContainersNodeName()), new Ref<Integer>(0));
      while (children.hasMoreElements()) {
        final ParentNode nextElement = children.nextElement();
        if (nextElement instanceof ContainerNode) {
          final ContainerNode containerNode = (ContainerNode)nextElement;
          Enumeration<MemberNode> memberNodes = containerNode.children();
          List<MemberNode> memberNodesList = new ArrayList<MemberNode>();
          while (memberNodes.hasMoreElements()) {
            memberNodesList.add(memberNodes.nextElement());
          }
          for (MemberNode memberNode : memberNodesList) {
            newRoot.add(memberNode);
          }
        } else {
          otherObjects.add(nextElement);
        }
      }
      replaceChildren(root, otherObjects);
      sortNode(newRoot, mySorted);
      if (newRoot.children().hasMoreElements()) root.add(newRoot);
    }
    else {
      Enumeration<ParentNode> children = getRootNodeChildren();
      if (children.hasMoreElements()) {
        ParentNode allClassesNode = children.nextElement();
        Enumeration<MemberNode> memberNodes = allClassesNode.children();
        ArrayList<MemberNode> arrayList = new ArrayList<MemberNode>();
        while (memberNodes.hasMoreElements()) {
          arrayList.add(memberNodes.nextElement());
        }
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

  protected String getAllContainersNodeName() {
    return IdeBundle.message("node.memberchooser.all.classes");
  }

  private Enumeration<ParentNode> getRootNodeChildren() {
    return getRootNode().children();
  }

  protected DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)myTreeModel.getRoot();
  }

  private Pair<ElementNode,List<ElementNode>> storeSelection() {
    List<ElementNode> selectedNodes = new ArrayList<ElementNode>();
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        selectedNodes.add((ElementNode)path.getLastPathComponent());
      }
    }
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    return Pair.create(leadSelectionPath != null ? (ElementNode)leadSelectionPath.getLastPathComponent() : null, selectedNodes);
  }


  private void restoreSelection(Pair<ElementNode,List<ElementNode>> pair) {
    List<ElementNode> selectedNodes = pair.second;

    DefaultMutableTreeNode root = getRootNode();

    ArrayList<TreePath> toSelect = new ArrayList<TreePath>();
    for (ElementNode node : selectedNodes) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
      if (root.isNodeDescendant(treeNode)) {
        toSelect.add(new TreePath(treeNode.getPath()));
      }
    }

    if (!toSelect.isEmpty()) {
      myTree.setSelectionPaths(toSelect.toArray(new TreePath[toSelect.size()]));
    }

    ElementNode leadNode = pair.first;
    if (leadNode != null) {
      myTree.setLeadSelectionPath(new TreePath(((DefaultMutableTreeNode)leadNode).getPath()));
    }
  }

  public void dispose() {
    PropertiesComponent instance = PropertiesComponent.getInstance();
    instance.setValue(PROP_SORTED, Boolean.toString(isSorted()));
    instance.setValue(PROP_SHOWCLASSES, Boolean.toString(myShowClasses));

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

  public void calcData(final DataKey key, final DataSink sink) {
    if (key.equals(LangDataKeys.PSI_ELEMENT)) {
      if (mySelectedElements != null && !mySelectedElements.isEmpty()) {
        T selectedElement = mySelectedElements.iterator().next();
        if (selectedElement instanceof ClassMemberWithElement) {
          sink.put(LangDataKeys.PSI_ELEMENT, ((ClassMemberWithElement) selectedElement).getElement());
        }
      }
    }
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();
      if (paths == null) return;
      for (int i = 0; i < paths.length; i++) {
        Object node = paths[i].getLastPathComponent();
        if (node instanceof MemberNode) {
          final MemberNode memberNode = (MemberNode)node;
          if (e.isAddedPath(i)) {
            if (!mySelectedNodes.contains(memberNode)) {
              mySelectedNodes.add(memberNode);
            }
          }
          else {
            mySelectedNodes.remove(memberNode);
          }
        }
      }
      mySelectedElements = new LinkedHashSet<T>();
      for (MemberNode selectedNode : mySelectedNodes) {
        mySelectedElements.add((T)selectedNode.getDelegate());
      }
    }
  }

  protected interface ElementNode extends MutableTreeNode {
    MemberChooserObject getDelegate();
    int getOrder();
  }

  protected interface MemberNode extends ElementNode {}

  protected abstract static class ElementNodeImpl extends DefaultMutableTreeNode implements ElementNode {
    private final int myOrder;
    private final MemberChooserObject myDelegate;

    public ElementNodeImpl(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      myOrder = order.get();
      order.set(myOrder + 1);
      myDelegate = delegate;
      if (parent != null) {
        parent.add(this);
      }
    }

    public MemberChooserObject getDelegate() {
      return myDelegate;
    }

    public int getOrder() {
      return myOrder;
    }
  }

  protected static class MemberNodeImpl extends ElementNodeImpl implements MemberNode {
    public MemberNodeImpl(ParentNode parent, ClassMember delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  protected static class ParentNode extends ElementNodeImpl {
    public ParentNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  protected static class ContainerNode extends ParentNode {
    public ContainerNode(DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  private class SelectNoneAction extends AbstractAction {
    public SelectNoneAction() {
      super(IdeBundle.message("action.select.none"));
    }

    public void actionPerformed(ActionEvent e) {
      myTree.clearSelection();
      doOKAction();
    }
  }

  private class TreeKeyListener extends KeyAdapter {
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

  private class SortEmAction extends ToggleAction {
    public SortEmAction() {
      super(IdeBundle.message("action.sort.alphabetically"),
            IdeBundle.message("action.sort.alphabetically"), AllIcons.ObjectBrowser.Sorted);
    }

    public boolean isSelected(AnActionEvent event) {
      return isSorted();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setSorted(flag);
    }
  }

  protected ShowContainersAction getShowContainersAction() {
    return new ShowContainersAction(IdeBundle.message("action.show.classes"),  PlatformIcons.CLASS_ICON);
  }

  protected class ShowContainersAction extends ToggleAction {
    public ShowContainersAction(final String text, final Icon icon) {
      super(text, text, icon);
    }

    public boolean isSelected(AnActionEvent event) {
      return myShowClasses;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setShowClasses(flag);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myContainerNodes.size() > 1);
    }
  }

  private class ExpandAllAction extends AnAction {
    public ExpandAllAction() {
      super(IdeBundle.message("action.expand.all"), IdeBundle.message("action.expand.all"),
            AllIcons.Actions.Expandall);
    }

    public void actionPerformed(AnActionEvent e) {
      TreeUtil.expandAll(myTree);
    }
  }

  private class CollapseAllAction extends AnAction {
    public CollapseAllAction() {
      super(IdeBundle.message("action.collapse.all"), IdeBundle.message("action.collapse.all"),
            AllIcons.Actions.Collapseall);
    }

    public void actionPerformed(AnActionEvent e) {
      TreeUtil.collapseAll(myTree, 1);
    }
  }

  private static class AlphaComparator implements Comparator<ElementNode> {
    public int compare(ElementNode n1, ElementNode n2) {
      return n1.getDelegate().getText().compareToIgnoreCase(n2.getDelegate().getText());
    }
  }

  protected static class OrderComparator implements Comparator<ElementNode> {
    public OrderComparator() {} // To make this class instanceable from the subclasses

    public int compare(ElementNode n1, ElementNode n2) {
      if (n1.getDelegate() instanceof ClassMemberWithElement
        &&  n2.getDelegate() instanceof ClassMemberWithElement) {
        return ((ClassMemberWithElement)n1.getDelegate()).getElement().getTextOffset()
          - ((ClassMemberWithElement)n2.getDelegate()).getElement().getTextOffset();
      }
      return n1.getOrder() - n2.getOrder();
    }
  }
}
