package com.intellij.codeInsight.template.impl;

import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.Alarm;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

class TemplateListPanel extends JPanel {

  private TreeTable myTreeTable;
  private JButton myCopyButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JButton myExportButton;
  private JButton myImportButton;
  private Editor myEditor;
  private SortedMap<TemplateKey,TemplateImpl> myTemplates = new TreeMap<TemplateKey, TemplateImpl>();
  private JComboBox myExpandByCombo;
  private boolean isModified = false;
  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");
  private static final String[] myColumnNames = {
    CodeInsightBundle.message("templates.dialog.table.column.abbreviation"),
    CodeInsightBundle.message("templates.dialog.table.column.description"),
    CodeInsightBundle.message("templates.dialog.table.column.active")};

  private DefaultMutableTreeNode myTreeRoot = new DefaultMutableTreeNode();
  private DefaultTreeModel myTreeTableModel;

  private Alarm myAlarm = new Alarm();
  private boolean myUpdateNeeded = false;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.TemplateListPanel");
  private static final Icon TEMPLATE_ICON = IconLoader.getIcon("/general/template.png");
  private static final Icon TEMPLATE_GROUP_ICON = IconLoader.getIcon("/general/templateGroup.png");

  public TemplateListPanel() {
    setLayout(new BorderLayout());
    fillPanel(this);
  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  public void reset() {
    TemplateSettings templateSettings = TemplateSettings.getInstance();
    initTemplates(templateSettings.getTemplates(), templateSettings.getLastSelectedTemplateKey());

    if (templateSettings.getDefaultShortcutChar() == TemplateSettings.TAB_CHAR) {
      myExpandByCombo.setSelectedItem(TAB);
    }
    else if (templateSettings.getDefaultShortcutChar() == TemplateSettings.ENTER_CHAR) {
      myExpandByCombo.setSelectedItem(ENTER);
    }
    else {
      myExpandByCombo.setSelectedItem(SPACE);
    }
    isModified = false;
    myUpdateNeeded = true;
  }

  public void apply() {
    TemplateSettings templateSettings = TemplateSettings.getInstance();
    templateSettings.setTemplates(getTemplates());
    templateSettings.setDefaultShortcutChar(getDefaultShortcutChar());
  }

  public boolean isModified() {
    TemplateSettings templateSettings = TemplateSettings.getInstance();
    if (templateSettings.getDefaultShortcutChar() != getDefaultShortcutChar()) {
      return true;
    }
    return isModified;
  }

  private char getDefaultShortcutChar() {
    Object selectedItem = myExpandByCombo.getSelectedItem();
    if (TAB.equals(selectedItem)) {
      return TemplateSettings.TAB_CHAR;
    }
    else if (ENTER.equals(selectedItem)) {
      return TemplateSettings.ENTER_CHAR;
    }
    else {
      return TemplateSettings.SPACE_CHAR;
    }
  }

  private TemplateImpl[] getTemplates() {
    TemplateImpl[] newTemplates = new TemplateImpl[myTemplates.size()];
    Iterator<TemplateKey> iterator = myTemplates.keySet().iterator();
    int i = 0;
    while (iterator.hasNext()) {
      newTemplates[i] = myTemplates.get(iterator.next());
      i++;
    }
    return newTemplates;
  }

  private void fillPanel(JPanel optionsPanel) {
    JPanel tablePanel = new JPanel();
    tablePanel.setBorder(BorderFactory.createLineBorder(Color.gray));
    tablePanel.setLayout(new BorderLayout());
    tablePanel.add(createTable(), BorderLayout.CENTER);

    JPanel tableButtonsPanel = new JPanel();
    tableButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    tableButtonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(0, 0, 4, 0);

    final JButton addButton = createButton(tableButtonsPanel, gbConstraints, CodeInsightBundle.message("templates.dialog.table.action.add"));
    addButton.setEnabled(true);
    myCopyButton = createButton(tableButtonsPanel, gbConstraints, CodeInsightBundle.message("templates.dialog.table.action.copy"));
    myEditButton = createButton(tableButtonsPanel, gbConstraints, CodeInsightBundle.message("templates.dialog.table.action.edit"));
    myRemoveButton = createButton(tableButtonsPanel, gbConstraints, CodeInsightBundle.message("templates.dialog.table.action.remove"));

    if (getSchemesManager().isImportExportAvailable()) {
      myExportButton = createButton(tableButtonsPanel, gbConstraints, "Export");
      myImportButton = createButton(tableButtonsPanel, gbConstraints, "Import");

      myExportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          exportCurrentGroup();
        }
      });

      myImportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          new SchemesToImportPopup<TemplateGroup>(TemplateListPanel.this){
            protected void onSchemeSelected(final TemplateGroup scheme) {
              for (TemplateImpl template : scheme.getTemplates()) {
                addTemplate(template);
                isModified =  true;
              }
            }
          }.show(getSchemesManager(), collectCurrentGroupNames());
        }
      });

    }

    gbConstraints.weighty = 1;
    tableButtonsPanel.add(new JPanel(), gbConstraints);

    tablePanel.add(tableButtonsPanel, BorderLayout.EAST);
    optionsPanel.add(tablePanel, BorderLayout.CENTER);

    JPanel textPanel = new JPanel(new BorderLayout());
    textPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    myEditor = TemplateEditorUtil.createEditor(true, "");
    textPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
    textPanel.add(createExpandByPanel(), BorderLayout.SOUTH);
    textPanel.setPreferredSize(new Dimension(100, myEditor.getLineHeight() * 12));

    optionsPanel.add(textPanel, BorderLayout.SOUTH);

    addButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          addRow();
        }
      }
    );

    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          copyRow();
        }
      }
    );

    myEditButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          edit();
        }
      }
    );

    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          removeRow();
        }
      }
    );
  }

  private void exportCurrentGroup() {
    int selected = myTreeTable.getSelectedRow();
    if (selected < 0) return;

    String groupName = getGroupName(selected);

    TemplateGroup group = new TemplateGroup(groupName);

    for (TemplateImpl template1 : myTemplates.values()) {
      if (template1.getGroupName().equals(groupName)) {
        group.addTemplate(template1);
      }
    }

    try {
      getSchemesManager().exportScheme(group);
    }
    catch (WriteExternalException e) {
      LOG.debug(e);
    }

  }

  private Collection<String> collectCurrentGroupNames() {
    HashSet<String> result = new HashSet<String>();
    for (TemplateImpl template : myTemplates.values()) {
      result.add(template.getGroupName());
    }

    return result;
  }

  private SchemesManager<TemplateGroup> getSchemesManager() {
    return (TemplateSettings.getInstance()).getSchemesManager();
  }

  private static JButton createButton(final JPanel tableButtonsPanel, final GridBagConstraints gbConstraints, final String message) {
    JButton button = new JButton(message);
    button.setEnabled(false);
    button.setMargin(new Insets(2, 4, 2, 4));
    tableButtonsPanel.add(button, gbConstraints);
    return button;
  }

  private JPanel createExpandByPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.weighty = 0;
    gbConstraints.insets = new Insets(4, 0, 0, 0);
    gbConstraints.weightx = 0;
    gbConstraints.gridy = 0;
//    panel.add(createLabel("By default expand with       "), gbConstraints);
    panel.add(new JLabel(CodeInsightBundle.message("templates.dialog.shortcut.chooser.label")), gbConstraints);

    gbConstraints.gridx = 1;
    myExpandByCombo = new JComboBox();
    myExpandByCombo.addItem(SPACE);
    myExpandByCombo.addItem(TAB);
    myExpandByCombo.addItem(ENTER);
    panel.add(myExpandByCombo, gbConstraints);

    gbConstraints.gridx = 2;
    gbConstraints.weightx = 1;
    panel.add(new JPanel(), gbConstraints);

    return panel;
  }

  private TemplateKey getTemplateKey(int row) {
    JTree tree = myTreeTable.getTree();
    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof TemplateImpl) {
        return new TemplateKey((TemplateImpl)node.getUserObject());
      }
    }

    return null;
  }

  private String getGroupName(int row) {
    JTree tree = myTreeTable.getTree();
    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof String) {
        return (String)node.getUserObject();
      }
    }

    return null;
  }

  private void edit() {
    int selected = myTreeTable.getSelectedRow();
    if (selected < 0) return;

    TemplateKey templateKey = getTemplateKey(selected);
    if (templateKey == null) return;

    TemplateImpl template = myTemplates.get(templateKey);
    EditTemplateDialog dialog = new EditTemplateDialog(this, CodeInsightBundle.message("dialog.edit.live.template.title"), template, getTemplates(),
                                                       (String)myExpandByCombo.getSelectedItem());
    dialog.show();
    if (!dialog.isOK()) return;

    myTemplates.remove(new TemplateKey(template));
    dialog.apply();
    myTemplates.put(new TemplateKey(template), template);

    AbstractTableModel model = (AbstractTableModel)myTreeTable.getModel();

    model.fireTableRowsUpdated(selected, selected);
    myTreeTable.setRowSelectionInterval(selected, selected);

    updateTemplateTextArea();
    isModified = true;
  }

  private void addRow() {
    TemplateImpl template = new TemplateImpl("", "", TemplateSettings.USER_GROUP_NAME);
    EditTemplateDialog dialog = new EditTemplateDialog(this, CodeInsightBundle.message("dialog.edit.live.template.title"), template, getTemplates(),
                                                       (String)myExpandByCombo.getSelectedItem());
    dialog.show();
    if (!dialog.isOK()) return;
    dialog.apply();

    addTemplate(template);
    isModified = true;
  }

  private void copyRow() {
    int selected = myTreeTable.getSelectedRow();
    if (selected < 0) return;

    TemplateKey templateKey = getTemplateKey(selected);
    LOG.assertTrue(templateKey != null);
    TemplateImpl template = (myTemplates.get(templateKey)).copy();
    EditTemplateDialog dialog = new EditTemplateDialog(this, CodeInsightBundle.message("dialog.copy.live.template.title"), template, getTemplates(),
                                                       (String)myExpandByCombo.getSelectedItem());
    dialog.show();
    if (!dialog.isOK()) return;

    dialog.apply();
    addTemplate(template);
    isModified = true;
  }

  private void removeRow() {
    int selected = myTreeTable.getSelectedRow();
    if (selected < 0) return;
    TemplateKey templateKey = getTemplateKey(selected);
    if (templateKey == null) return;

    int result = Messages.showOkCancelDialog(this, CodeInsightBundle.message("template.delete.confirmation.text"),
                                             CodeInsightBundle.message("template.delete.confirmation.title"),
                                             Messages.getQuestionIcon());
    if (result != DialogWrapper.OK_EXIT_CODE) return;

    removeTemplateAt(selected);
    isModified = true;
  }

  private JScrollPane createTable() {
    ColumnInfo[] columnInfos = {new MyColumnInfo(myColumnNames[0]) {
      public Class getColumnClass() {
        return TreeTableModel.class;
      }

      public boolean isCellEditable(Object o) {
        return true;
      }
    }, new MyColumnInfo(myColumnNames[1]), new ActivationStateColumnInfo(myColumnNames[2])};

    myTreeRoot = new DefaultMutableTreeNode();

    myTreeTableModel = new ListTreeTableModelOnColumns(myTreeRoot, columnInfos);
    myTreeTable = new MyTable((ListTreeTableModelOnColumns)myTreeTableModel);
    myTreeTable.setRootVisible(false);
    myTreeTable.setDefaultRenderer(Boolean.class, new BooleanTableCellRenderer());
    myTreeTable.getTree().setShowsRootHandles(true);
    myTreeTable.getTableHeader().setReorderingAllowed(false);

    myTreeTable.setTreeCellRenderer(new ColoredTreeCellRenderer () {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        value = ((DefaultMutableTreeNode)value).getUserObject();

        setPaintFocusBorder(false);
        if (value instanceof TemplateImpl) {
          setIcon(TEMPLATE_ICON);
          append (((TemplateImpl)value).getKey(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else if (value instanceof String) {
          setIcon(TEMPLATE_GROUP_ICON);
          append ((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });

    myTreeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTreeTable.setPreferredScrollableViewportSize(new Dimension(300, myTreeTable.getRowHeight() * 10));
    myTreeTable.getColumn(myColumnNames[0]).setPreferredWidth(80);
    myTreeTable.getColumn(myColumnNames[1]).setPreferredWidth(260);
    myTreeTable.getColumn(myColumnNames[2]).setPreferredWidth(12);

    myTreeTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          addRow();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
      JComponent.WHEN_FOCUSED
    );

    myTreeTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          removeRow();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_FOCUSED
    );

    myTreeTable.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && myTreeTable.columnAtPoint(e.getPoint()) != 2) {
            edit();
          }
        }
      }
    );

    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTreeTable);
    if (myTemplates.size() > 0) {
      myTreeTable.setRowSelectionInterval(0, 0);
    }
    scrollpane.setPreferredSize(new Dimension(600, 400));
    return scrollpane;
  }

  private void updateTemplateTextArea() {
    if (!myUpdateNeeded) return;

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            int selected = myTreeTable.getSelectedRow();
            if (selected < 0 || selected >= myTemplates.size()) {
              myEditor.getDocument().replaceString(0, myEditor.getDocument().getTextLength(), "");
            }
            else {
              TemplateKey templateKey = getTemplateKey(selected);
              if (templateKey != null) {
                TemplateImpl template = myTemplates.get(templateKey);
                String text = template.getString();
                myEditor.getDocument().replaceString(0, myEditor.getDocument().getTextLength(), text);
                TemplateEditorUtil.setHighlighter(myEditor, template.getTemplateContext());
              } else {
                myEditor.getDocument().replaceString(0, myEditor.getDocument().getTextLength(), "");
              }
            }
          }
        });
      }
    }, 100);
  }

  private void addTemplate(TemplateImpl template) {
    myTemplates.put(new TemplateKey(template), template);

    DefaultMutableTreeNode node = new DefaultMutableTreeNode(template);
    if (myTreeRoot.getChildCount() > 0) {
      for (DefaultMutableTreeNode child = (DefaultMutableTreeNode)myTreeRoot.getFirstChild();
           child != null;
           child = (DefaultMutableTreeNode)myTreeRoot.getChildAfter(child)) {
        String group = (String)child.getUserObject();
        if (group.equals(template.getGroupName())) {
          int index = getIndexToInsert (child, template.getKey());
          child.insert(node, index);
          myTreeTableModel.nodesWereInserted(child, new int[]{index});
          setSelectedNode(node);
          return;
        }
      }
    }
    int index = getIndexToInsert(myTreeRoot, template.getGroupName());
    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(template.getGroupName());
    myTreeRoot.insert(groupNode, index);
    myTreeTableModel.nodesWereInserted(myTreeRoot, new int[]{index});

    groupNode.add(node);
    myTreeTableModel.nodesWereInserted(groupNode, new int[]{0});

    setSelectedNode(node);
  }

  private int getIndexToInsert(DefaultMutableTreeNode parent, String key) {
    if (parent.getChildCount() == 0) return 0;

    int res = 0;
    for (DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getFirstChild();
         child != null;
         child = (DefaultMutableTreeNode)parent.getChildAfter(child)) {
      Object o = child.getUserObject();
      String key1 = o instanceof TemplateImpl ? ((TemplateImpl)o).getKey() : (String)o;
      if (key1.compareTo(key) > 0) return res;
      res++;
    }
    return res;
  }

  private void setSelectedNode(DefaultMutableTreeNode node) {
    JTree tree = myTreeTable.getTree();
    TreePath path = new TreePath(node.getPath());
    tree.expandPath(path.getParentPath());
    int row = tree.getRowForPath(path);
    myTreeTable.getSelectionModel().setSelectionInterval(row, row);
    myTreeTable.scrollRectToVisible(myTreeTable.getCellRect(row, 0, true));
  }

  private void removeTemplateAt(int row) {
    JTree tree = myTreeTable.getTree();
    TreePath path = tree.getPathForRow(row);
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    LOG.assertTrue(node.getUserObject() instanceof TemplateImpl);

    TemplateImpl template = (TemplateImpl)node.getUserObject();
    myTemplates.remove(new TemplateKey(template));

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
    TreePath treePathToSelect = (parent.getChildAfter(node) != null || parent.getChildCount() == 1 ?
                            tree.getPathForRow(row + 1) :
                            tree.getPathForRow(row - 1));
    DefaultMutableTreeNode toSelect = treePathToSelect != null ? (DefaultMutableTreeNode)treePathToSelect.getLastPathComponent() : null;

    removeNodeFromParent(node);
    if (parent.getChildCount() == 0) removeNodeFromParent(parent);
    if (toSelect != null) {
      setSelectedNode(toSelect);
    }
  }

  private void removeNodeFromParent(DefaultMutableTreeNode node) {
    TreeNode parent = node.getParent();
    int idx = myTreeTableModel.getIndexOfChild(parent, node);
    node.removeFromParent();
    myTreeTableModel.nodesWereRemoved(parent, new int[]{idx}, new TreeNode[]{node});
  }

  private void initTemplates(TemplateImpl[] templates, String lastSelectedKey) {
    myTreeRoot.removeAllChildren();
    SortedMap<String, List<TemplateImpl>> groups = new TreeMap<String, List<TemplateImpl>>();
    for (TemplateImpl template1 : templates) {
      TemplateImpl template = template1.copy();
      myTemplates.put(new TemplateKey(template), template);
      String group = template.getGroupName();
      List<TemplateImpl> ts = groups.get(group);
      if (ts == null) {
        ts = new ArrayList<TemplateImpl>();
        groups.put(group, ts);
      }
      ts.add(template);
    }

    DefaultMutableTreeNode nodeToSelect = null;
    for (Map.Entry<String, List<TemplateImpl>> entry : groups.entrySet()) {
      String group = entry.getKey();
      List<TemplateImpl> groupTemplates = entry.getValue();
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
      for (final Object groupTemplate : groupTemplates) {
        TemplateImpl template = (TemplateImpl)groupTemplate;
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(template);
        groupNode.add(node);

        if (lastSelectedKey != null && lastSelectedKey.equals(template.getKey())) {
          nodeToSelect = node;
        }
      }
      myTreeRoot.add(groupNode);
    }

    myTreeTableModel.nodeStructureChanged(myTreeRoot);

    if (nodeToSelect != null) {
      JTree tree = myTreeTable.getTree();
      TreePath path = new TreePath(nodeToSelect.getPath());
      tree.expandPath(path.getParentPath());
      int rowToSelect = tree.getRowForPath(path);
      myTreeTable.getSelectionModel().setSelectionInterval(rowToSelect, rowToSelect);
      final Rectangle rect = myTreeTable.getCellRect(rowToSelect, 0, true);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myTreeTable.scrollRectToVisible(rect);
            }
          });
    }
  }

  private class MyTable extends TreeTableView {

    public MyTable(ListTreeTableModelOnColumns model) {
      super(model);
    }

    public void valueChanged(ListSelectionEvent event) {
      super.valueChanged(event);

      boolean enableEditButtons = false;
      boolean enableCopyButton = false;
      boolean enableExportButtons = false;

      int selected = getSelectedRow();
      if (selected >= 0 && selected < myTreeTable.getRowCount()) {
        TemplateSettings templateSettings = TemplateSettings.getInstance();
        TemplateKey templateKey = getTemplateKey(selected);
        if (templateKey != null) {
          TemplateImpl template = myTemplates.get(templateKey);
          if (template != null) {
            templateSettings.setLastSelectedTemplateKey(template.getKey());
          }
        } else {
          templateSettings.setLastSelectedTemplateKey(null);
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTreeTable.getTree().getPathForRow(selected).getLastPathComponent();
        enableExportButtons = false;
        enableEditButtons = false;
        enableCopyButton = false;
        if (node.getUserObject() instanceof TemplateImpl) {
          enableCopyButton = true;
          TemplateGroup group = getSchemesManager().findSchemeByName(((TemplateImpl)node.getUserObject()).getGroupName());
          if (group != null && !getSchemesManager().isShared(group)) {
            enableEditButtons = true;
          }
        }
        if (node.getUserObject() instanceof String) {
          String groupName = (String)node.getUserObject();
          TemplateGroup group = getSchemesManager().findSchemeByName(groupName);
          if (group != null) {
            enableExportButtons = !getSchemesManager().isShared(group);
          }

        }

      }
      updateTemplateTextArea();
      myEditor.getComponent().setEnabled(enableEditButtons);
      myExpandByCombo.setEnabled(enableEditButtons);
      
      if (myCopyButton != null) {
        myCopyButton.setEnabled(enableCopyButton);
        myEditButton.setEnabled(enableEditButtons);
        myRemoveButton.setEnabled(enableEditButtons);
      }

      if (myExportButton != null) {
        myExportButton.setEnabled(enableExportButtons);
        myImportButton.setEnabled(true);
      }
    }
  }


  private static class TemplateKey implements Comparable {
    private String myKey;
    private String myGroupName;

    public TemplateKey(TemplateImpl template) {
      myKey = template.getKey();
      if (myKey == null) {
        myKey = "";
      }
      myGroupName = template.getGroupName();
      if (myGroupName == null) {
        myGroupName = "";
      }
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof TemplateKey)) {
        return false;
      }
      TemplateKey templateKey = (TemplateKey)obj;
      return (myGroupName.compareTo(templateKey.myGroupName) == 0) &&
             (myKey.compareTo(templateKey.myKey) == 0);
    }

    public int compareTo(Object obj) {
      if (!(obj instanceof TemplateKey)) {
        return 1;
      }
      TemplateKey templateKey = (TemplateKey)obj;
      int result = myGroupName.compareTo(templateKey.myGroupName);
      return result != 0 ? result : myKey.compareTo(templateKey.myKey);
    }
  }

  class ActivationStateColumnInfo extends ColumnInfo {
    public ActivationStateColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(Object o) {
      return o != null;
    }

    public void setValue(Object obj, Object aValue) {
      obj = ((DefaultMutableTreeNode)obj).getUserObject();
      if (obj instanceof TemplateImpl) {
        TemplateImpl template = (TemplateImpl)obj;
        boolean state = !((Boolean)aValue).booleanValue();
        if (state != template.isDeactivated()) {
          template.setDeactivated(!((Boolean)aValue).booleanValue());
          isModified = true;
        }
      }
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public Object valueOf(Object object) {
      object = ((DefaultMutableTreeNode)object).getUserObject();
      if (object instanceof TemplateImpl) {
        return ((TemplateImpl)object).isDeactivated() ? Boolean.FALSE : Boolean.TRUE;
      }
      else {
        return null;
      }
    }

  }

  class MyColumnInfo extends ColumnInfo {
    public MyColumnInfo(String name) {super(name);}

    public Object valueOf(Object object) {
      object = ((DefaultMutableTreeNode)object).getUserObject();
      if (object instanceof TemplateImpl) {
        TemplateImpl template = (TemplateImpl)object;
        if (getName().equals(myColumnNames[0])) {
          return template.getKey();
        }
        else if (getName().equals(myColumnNames[1])) {
          return template.getDescription();
        }
      }
      else if (object instanceof String) {
        if (getName().equals(myColumnNames[0])) {
          return object;
        }
        else {
          return null;
        }
      }
      LOG.assertTrue(false);
      return null;
    }

    public Comparator getComparator() {
      if (myColumnNames[0].equals(getName())) {
        return new Comparator() {
          public int compare(Object o, Object o1) {
            o = ((DefaultMutableTreeNode)o).getUserObject();
            o1 = ((DefaultMutableTreeNode)o1).getUserObject();
            if (o instanceof TemplateImpl && o1 instanceof TemplateImpl) {
              return ((TemplateImpl)o).getKey().compareTo(((TemplateImpl)o1).getKey());
            }
            else if (o instanceof String && o1 instanceof String) {
              return ((String)o).compareTo(((String)o1));
            }
            return 0;
          }
        };
      }

      return null;
    }
  }
}

