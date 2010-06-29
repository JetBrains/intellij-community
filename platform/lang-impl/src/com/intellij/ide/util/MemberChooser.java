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
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Icons;
import com.intellij.util.SmartList;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class MemberChooser<T extends ClassMember> extends DialogWrapper implements TypeSafeDataProvider {
  protected Tree myTree;
  private DefaultTreeModel myTreeModel;
  private JCheckBox myCopyJavadocCheckbox;
  private JCheckBox myInsertOverrideAnnotationCheckbox;

  private final ArrayList<MemberNode> mySelectedNodes = new ArrayList<MemberNode>();

  private boolean mySorted = false;
  private boolean myShowClasses = true;
  private boolean myAllowEmptySelection = false;
  private boolean myAllowMultiSelection;
  private final Project myProject;
  private final boolean myIsInsertOverrideVisible;
  private final JComponent myHeaderPanel;

  private T[] myElements;
  private final HashMap<MemberNode,ParentNode> myNodeToParentMap = new HashMap<MemberNode, ParentNode>();
  private final HashMap<ClassMember, MemberNode> myElementToNodeMap = new HashMap<ClassMember, MemberNode>();
  private final ArrayList<ContainerNode> myContainerNodes = new ArrayList<ContainerNode>();
  private LinkedHashSet<T> mySelectedElements;

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
    super(project, true);
    myAllowEmptySelection = allowEmptySelection;
    myAllowMultiSelection = allowMultiSelection;
    myProject = project;
    myIsInsertOverrideVisible = isInsertOverrideVisible;
    myHeaderPanel = headerPanel;
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
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

    TreeUtil.expandAll(myTree);
    myCopyJavadocCheckbox = new NonFocusableCheckBox(IdeBundle.message("checkbox.copy.javadoc"));
    if (myIsInsertOverrideVisible) {
      myInsertOverrideAnnotationCheckbox = new NonFocusableCheckBox(IdeBundle.message("checkbox.insert.at.override"));
    }

    myTree.doLayout();
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
        if (key instanceof PsiElementMemberChooserObject) {
            final ContainerNode containerNode = new ContainerNode(rootNode, key, count);
            node = containerNode;
            myContainerNodes.add(containerNode);
        }
        if (node == null) {
          node = new ParentNode(rootNode, key, count);
        }
        return node;
      }
    };

    for (T object : myElements) {
      final ParentNode parentNode = map.get(object.getParentNodeDelegate());
      final MemberNode elementNode = new MemberNode(parentNode, object, count);
      myNodeToParentMap.put(elementNode, parentNode);
      myElementToNodeMap.put(object, elementNode);
    }
    return new DefaultTreeModel(rootNode);
  }

  public void selectElements(ClassMember[] elements) {
    ArrayList<TreePath> selectionPaths = new ArrayList<TreePath>();
    for (ClassMember element : elements) {
      MemberNode treeNode = myElementToNodeMap.get(element);
      if (treeNode != null) {
        selectionPaths.add(new TreePath(treeNode.getPath()));
      }
    }
    myTree.setSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
  }


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

  protected List<JComponent> customizeOptionsPanel() {
    final SmartList<JComponent> list = new SmartList<JComponent>();

    if (myIsInsertOverrideVisible) {
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();
      myInsertOverrideAnnotationCheckbox.setSelected(styleSettings.INSERT_OVERRIDE_ANNOTATION);
      list.add(myInsertOverrideAnnotationCheckbox);
    }

    myCopyJavadocCheckbox.setSelected(PropertiesComponent.getInstance().isTrueValue(PROP_COPYJAVADOC));
    list.add(myCopyJavadocCheckbox);
    return list;
  }

  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JPanel optionsPanel = new JPanel(new VerticalFlowLayout());
    for (final JComponent component : customizeOptionsPanel()) {
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
      new CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)),
      myTree);
    group.add(expandAllAction);

    CollapseAllAction collapseAllAction = new CollapseAllAction();
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)),
      myTree);
    group.add(collapseAllAction);

    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(),
              BorderLayout.NORTH);

    // Tree

    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
      if (value instanceof ElementNode) {
        ((ElementNode) value).getDelegate().renderTreeNode(this, tree);
      }
    }
  }
);
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
    TreeUtil.expandAll(myTree);
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Nullable
      public String convert(TreePath path) {
        final MemberChooserObject delegate = ((ElementNode)path.getLastPathComponent()).getDelegate();
        return delegate.getText();
      }
    });
    myTree.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            if (myTree.getPathForLocation(e.getX(), e.getY()) != null) {
              doOKAction();
            }
          }
        }
      }
    );
    TreeUtil.installActions(myTree);
    JScrollPane scrollPane = new JScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(350, 450));
    panel.add(scrollPane, BorderLayout.CENTER);

    return panel;
  }

  protected void fillToolbarActions(DefaultActionGroup group) {
    SortEmAction sortAction = new SortEmAction();
    sortAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_MASK)), myTree);
    setSorted(PropertiesComponent.getInstance().isTrueValue(PROP_SORTED));
    group.add(sortAction);

    ShowContainersAction showContainersAction = getShowContainersAction();
    showContainersAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_MASK)), myTree);
    setShowClasses(PropertiesComponent.getInstance().isTrueValue(PROP_SHOWCLASSES));
    group.add(showContainersAction);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.MemberChooser";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
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

    TreeUtil.expandAll(myTree);

    restoreSelection(selection);
  }

  protected String getAllContainersNodeName() {
    return IdeBundle.message("node.memberchooser.all.classes");
  }

  private Enumeration<ParentNode> getRootNodeChildren() {
    return getRootNode().children();
  }

  private DefaultMutableTreeNode getRootNode() {
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
      if (root.isNodeDescendant(node)) {
        toSelect.add(new TreePath(node.getPath()));
      }
    }

    if (!toSelect.isEmpty()) {
      myTree.setSelectionPaths(toSelect.toArray(new TreePath[toSelect.size()]));
    }

    ElementNode leadNode = pair.first;
    if (leadNode != null) {
      myTree.setLeadSelectionPath(new TreePath(leadNode.getPath()));
    }
  }

  public void dispose() {
    PropertiesComponent instance = PropertiesComponent.getInstance();
    instance.setValue(PROP_SORTED, Boolean.toString(isSorted()));
    instance.setValue(PROP_SHOWCLASSES, Boolean.toString(myShowClasses));
    instance.setValue(PROP_COPYJAVADOC, Boolean.toString(myCopyJavadocCheckbox.isSelected()));

    getContentPane().removeAll();
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

  private abstract static class ElementNode extends DefaultMutableTreeNode {
    private final int myOrder;
    private final MemberChooserObject myDelegate;

    public ElementNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
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

  private static class MemberNode extends ElementNode {
    public MemberNode(ParentNode parent, ClassMember delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  private static class ParentNode extends ElementNode {
    public ParentNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  private static class ContainerNode extends ParentNode {
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
          final ElementNode node = (ElementNode)lastComponent;
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
            IdeBundle.message("action.sort.alphabetically"), IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return isSorted();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setSorted(flag);
    }
  }

  protected ShowContainersAction getShowContainersAction() {
    return new ShowContainersAction(IdeBundle.message("action.show.classes"),  Icons.CLASS_ICON);
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
            IconLoader.getIcon("/actions/expandall.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      TreeUtil.expandAll(myTree);
    }
  }

  private class CollapseAllAction extends AnAction {
    public CollapseAllAction() {
      super(IdeBundle.message("action.collapse.all"), IdeBundle.message("action.collapse.all"),
            IconLoader.getIcon("/actions/collapseall.png"));
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

  private static class OrderComparator implements Comparator<ElementNode> {
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
