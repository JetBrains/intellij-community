package com.intellij.codeInsight.template.impl;

import com.intellij.application.options.ExportSchemeAction;
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
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class TemplateListPanel extends JPanel {

  private CheckboxTree myTree;
  private JButton myCopyButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JButton myExportButton;
  private JButton myImportButton;
  private Editor myEditor;
  private List<TemplateGroup> myTemplateGroups = new ArrayList<TemplateGroup>();
  private JComboBox myExpandByCombo;
  private boolean isModified = false;
  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");

  private CheckedTreeNode myTreeRoot = new CheckedTreeNode(null);

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
    List<TemplateGroup> groups = new ArrayList<TemplateGroup>(templateSettings.getTemplateGroups());

    Collections.sort(groups, new Comparator<TemplateGroup>(){
      public int compare(final TemplateGroup o1, final TemplateGroup o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    initTemplates(groups, templateSettings.getLastSelectedTemplateKey());



    if (templateSettings.getDefaultShortcutChar() == TemplateSettings.TAB_CHAR) {
      myExpandByCombo.setSelectedItem(TAB);
    }
    else if (templateSettings.getDefaultShortcutChar() == TemplateSettings.ENTER_CHAR) {
      myExpandByCombo.setSelectedItem(ENTER);
    }
    else {
      myExpandByCombo.setSelectedItem(SPACE);
    }

    updateTemplateText();

    isModified = false;
    myUpdateNeeded = true;


  }

  public void apply() {
    TemplateSettings templateSettings = TemplateSettings.getInstance();
    templateSettings.setTemplates(getTemplateGroups());
    templateSettings.setDefaultShortcutChar(getDefaultShortcutChar());
    isModified = false;
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

  private List<TemplateGroup> getTemplateGroups() {
    return myTemplateGroups;
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

    if (getSchemesManager().isExportAvailable()) {
      myExportButton = createButton(tableButtonsPanel, gbConstraints, "Share...");
      myEditButton.setMnemonic('S');

      myExportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          exportCurrentGroup();
        }
      });


    }

    if (getSchemesManager().isImportAvailable()) {
      myImportButton = createButton(tableButtonsPanel, gbConstraints, "Import Shared...");
      myImportButton.setMnemonic('I');
      myImportButton.setEnabled(true);

      myImportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          new SchemesToImportPopup<TemplateGroup, TemplateGroup>(TemplateListPanel.this){
            protected void onSchemeSelected(final TemplateGroup scheme) {
              for (TemplateImpl newTemplate : scheme.getElements()) {
                for (TemplateImpl existingTemplate : collectAllTemplates()) {
                  if (existingTemplate.getKey().equals(newTemplate.getKey())) {
                    Messages.showMessageDialog(
                      TemplateListPanel.this,
                      CodeInsightBundle.message("dialog.edit.template.error.already.exists", existingTemplate.getKey(), existingTemplate.getGroupName()),
                      CodeInsightBundle.message("dialog.edit.template.error.title"),
                      Messages.getErrorIcon()
                    );
                    return;
                  }
                }
              }
              insertNewGroup(scheme);
              for (TemplateImpl template : scheme.getElements()) {
                addTemplate(template);
                isModified =  true;
              }
            }
          }.show(getSchemesManager(), myTemplateGroups);
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

  private Iterable<? extends TemplateImpl> collectAllTemplates() {
    ArrayList<TemplateImpl> result = new ArrayList<TemplateImpl>();
    for (TemplateGroup templateGroup : myTemplateGroups) {
      result.addAll(templateGroup.getElements());
    }
    return result;
  }

  private void exportCurrentGroup() {
    int selected = getSelectedIndex();
    if (selected < 0) return;

    ExportSchemeAction.doExport(getGroup(selected), getSchemesManager());

  }

  private static SchemesManager<TemplateGroup, TemplateGroup> getSchemesManager() {
    return (TemplateSettings.getInstance()).getSchemesManager();
  }

  private static JButton createButton(final JPanel tableButtonsPanel, final GridBagConstraints gbConstraints, final String message) {
    JButton button = new JButton(message);
    button.setEnabled(false);
    //button.setMargin(new Insets(2, 4, 2, 4));
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
    JTree tree = myTree;
    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof TemplateImpl) {
        return new TemplateKey((TemplateImpl)node.getUserObject());
      }
    }

    return null;
  }

  private TemplateImpl getTemplate(int row) {
    JTree tree = myTree;
    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof TemplateImpl) {
        return (TemplateImpl)node.getUserObject();
      }
    }

    return null;
  }

  private TemplateGroup getGroup(int row) {
    JTree tree = myTree;
    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof TemplateGroup) {
        return (TemplateGroup)node.getUserObject();
      }
    }

    return null;
  }

  private void edit() {
    int selected = getSelectedIndex();
    if (selected < 0) return;

    TemplateImpl template = getTemplate(selected);
    DefaultMutableTreeNode oldTemplateNode = getNode(selected);
    if (template == null) return;

    String oldGroupName = template.getGroupName();

    EditTemplateDialog dialog = new EditTemplateDialog(this, CodeInsightBundle.message("dialog.edit.live.template.title"), template, getTemplateGroups(),
                                                       (String)myExpandByCombo.getSelectedItem());
    dialog.show();
    if (!dialog.isOK()) return;

    TemplateGroup group = getTemplateGroup(template.getGroupName());

    LOG.assertTrue(group != null, template.getGroupName());

    dialog.apply();

    DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();

    if (!oldGroupName.equals(template.getGroupName())) {
      TemplateGroup oldGroup = getTemplateGroup(oldGroupName);
      oldGroup.removeElement(template);

      template.setId(null);//To make it not equal with default template with the same name

      JTree tree = myTree;
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)oldTemplateNode.getParent();
      removeNodeFromParent(oldTemplateNode);
      if (parent.getChildCount() == 0) removeNodeFromParent(parent);

      DefaultMutableTreeNode templateNode = addTemplate(template);

      TreePath newTemplatePath = new TreePath(templateNode.getPath());
      tree.expandPath(newTemplatePath);

      selected = tree.getRowForPath(newTemplatePath);

      ((DefaultTreeModel)myTree.getModel()).nodeStructureChanged(myTreeRoot);
    }

    myTree.setSelectionInterval(selected, selected);

    updateTemplateTextArea();
    isModified = true;
  }

  private DefaultMutableTreeNode getNode(final int row) {
    JTree tree = myTree;
    TreePath path = tree.getPathForRow(row);
    if (path != null) {
      return (DefaultMutableTreeNode)path.getLastPathComponent();
    }

    return null;

  }

  private DefaultMutableTreeNode getGroupNode(final String groupName) {
    for (int i = 0; i < myTreeRoot.getChildCount(); i++) {
      DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)myTreeRoot.getChildAt(i);
      if (((TemplateGroup)groupNode.getUserObject()).getName().equals(groupName)) {
        return groupNode;
      }
    }

    return null;
  }

  private TemplateGroup getTemplateGroup(final String groupName) {
    for (TemplateGroup group : myTemplateGroups) {
      if (group.getName().equals(groupName)) return group;
    }

    return null;
  }

  private void addRow() {
    TemplateImpl template = new TemplateImpl("", "", TemplateSettings.USER_GROUP_NAME);
    EditTemplateDialog dialog = new EditTemplateDialog(this, CodeInsightBundle.message("dialog.add.live.template.title"), template, getTemplateGroups(),
                                                       (String)myExpandByCombo.getSelectedItem());
    dialog.show();
    if (!dialog.isOK()) return;
    dialog.apply();

    addTemplate(template);
    isModified = true;
  }

  private void copyRow() {
    int selected = getSelectedIndex();
    if (selected < 0) return;

    TemplateImpl orTemplate = getTemplate(selected);
    LOG.assertTrue(orTemplate != null);
    TemplateImpl template = orTemplate.copy();
    EditTemplateDialog dialog = new EditTemplateDialog(this, CodeInsightBundle.message("dialog.copy.live.template.title"), template, getTemplateGroups(),
                                                       (String)myExpandByCombo.getSelectedItem());
    dialog.show();
    if (!dialog.isOK()) return;

    dialog.apply();
    addTemplate(template);
    isModified = true;
  }

  private int getSelectedIndex() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) {
      return -1;
    }
    else {
      return myTree.getRowForPath(selectionPath);
    }

  }

  private void removeRow() {
    int selected = getSelectedIndex(); // TODO
    if (selected < 0) return;
    TemplateKey templateKey = getTemplateKey(selected);
    if (templateKey != null) {
      int result = Messages.showOkCancelDialog(this, CodeInsightBundle.message("template.delete.confirmation.text"),
                                               CodeInsightBundle.message("template.delete.confirmation.title"),
                                               Messages.getQuestionIcon());
      if (result != DialogWrapper.OK_EXIT_CODE) return;

      removeTemplateAt(selected);
      isModified = true;
    }
    else {
      TemplateGroup group = getGroup(selected);
      if (group != null) {
        int result = Messages.showOkCancelDialog(this, CodeInsightBundle.message("template.delete.group.confirmation.text"),
                                                 CodeInsightBundle.message("template.delete.confirmation.title"),
                                                 Messages.getQuestionIcon());
        if (result != DialogWrapper.OK_EXIT_CODE) return;

        JTree tree = myTree;
        TreePath path = tree.getPathForRow(selected);

        myTemplateGroups.remove(group);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        removeNodeFromParent(node);

        isModified = true;
      }

    }

  }

  private JScrollPane createTable() {
    myTreeRoot = new CheckedTreeNode(null);

    myTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(){
      public void customizeCellRenderer(final JTree tree,
                                        Object value,
                                        final boolean selected,
                                        final boolean expanded,
                                        final boolean leaf,
                                        final int row,
                                        final boolean hasFocus) {

        value = ((DefaultMutableTreeNode)value).getUserObject();

        if (value instanceof TemplateImpl) {
          //getTextRenderer().setIcon(TEMPLATE_ICON);
          getTextRenderer().append (((TemplateImpl)value).getKey(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          String description = ((TemplateImpl)value).getDescription();
          if (description != null && description.length() > 0) {
            getTextRenderer().append (" (" + description + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
        else if (value instanceof TemplateGroup) {
          //getTextRenderer().setIcon(TEMPLATE_GROUP_ICON);
          getTextRenderer().append (((TemplateGroup)value).getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }


      }
    }, myTreeRoot) {
      @Override
      protected void onNodeStateChanged(final CheckedTreeNode node) {
        Object obj = node.getUserObject();
        if (obj instanceof TemplateImpl) {
          ((TemplateImpl)obj).setDeactivated(!node.isChecked());
        }
      }

    };
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    DefaultTreeSelectionModel selModel = new DefaultTreeSelectionModel();
    myTree.setSelectionModel(selModel);
    selModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener(){
      public void valueChanged(final TreeSelectionEvent e) {
        boolean enableEditButton = false;
        boolean enableRemoveButton = false;
        boolean enableCopyButton = false;
        boolean enableExportButton = false;

        int selected = getSelectedIndex();
        if (selected >= 0 && selected < myTree.getRowCount()) {
          TemplateSettings templateSettings = TemplateSettings.getInstance();
          TemplateImpl template = getTemplate(selected);
          if (template != null) {
            templateSettings.setLastSelectedTemplateKey(template.getKey());
          } else {
            templateSettings.setLastSelectedTemplateKey(null);
          }
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getPathForRow(selected).getLastPathComponent();
          enableExportButton = false;
          enableEditButton = false;
          enableCopyButton = false;
          if (node.getUserObject() instanceof TemplateImpl) {
            enableCopyButton = true;
            TemplateGroup group = getTemplateGroup(template.getGroupName());
            if (group != null && !getSchemesManager().isShared(group)) {
              enableEditButton = true;
              enableRemoveButton = true;
            }
          }
          if (node.getUserObject() instanceof TemplateGroup) {
            enableRemoveButton = true;
            TemplateGroup group = (TemplateGroup)node.getUserObject();
            enableExportButton = !getSchemesManager().isShared(group);

          }

        }
        updateTemplateTextArea();
        myEditor.getComponent().setEnabled(enableEditButton);

        if (myCopyButton != null) {
          myCopyButton.setEnabled(enableCopyButton);
          myEditButton.setEnabled(enableEditButton);
          myRemoveButton.setEnabled(enableRemoveButton);
        }

        if (myExportButton != null) {
          myExportButton.setEnabled(enableExportButton);
        }

        if (myImportButton != null) {
          myImportButton.setEnabled(true);
        }

      }
    });


    myTree.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          addRow();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
      JComponent.WHEN_FOCUSED
    );

    myTree.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          removeRow();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_FOCUSED
    );

    myTree.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            edit();
          }
        }
      }
    );

    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTree);
    if (myTemplateGroups.size() > 0) {
      myTree.setSelectionInterval(0, 0);
    }
    scrollpane.setPreferredSize(new Dimension(600, 400));
    return scrollpane;
  }

  private void updateTemplateTextArea() {
    if (!myUpdateNeeded) return;

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        updateTemplateText();
      }
    }, 100);
  }

  private void updateTemplateText() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        int selected = getSelectedIndex();
        if (selected < 0) {
          myEditor.getDocument().replaceString(0, myEditor.getDocument().getTextLength(), "");
        }
        else {
          TemplateImpl template = getTemplate(selected);
          if (template != null) {
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

  private DefaultMutableTreeNode addTemplate(TemplateImpl template) {
    TemplateGroup newGroup = getTemplateGroup(template.getGroupName());
    if (newGroup == null) {
      newGroup = new TemplateGroup(template.getGroupName());
      insertNewGroup(newGroup);
    }
    if (!newGroup.contains(template)) {
      newGroup.addElement(template);
    }

    CheckedTreeNode node = new CheckedTreeNode(template);
    node.setChecked(template.isDeactivated());
    if (myTreeRoot.getChildCount() > 0) {
      for (DefaultMutableTreeNode child = (DefaultMutableTreeNode)myTreeRoot.getFirstChild();
           child != null;
           child = (DefaultMutableTreeNode)myTreeRoot.getChildAfter(child)) {
        if (((TemplateGroup)child.getUserObject()).getName().equals(template.getGroupName())) {
          int index = getIndexToInsert (child, template.getKey());
          child.insert(node, index);
          ((DefaultTreeModel)myTree.getModel()).nodesWereInserted(child, new int[]{index});
          setSelectedNode(node);
          return node;
        }
      }
    }

    return null;
  }

  private void insertNewGroup(final TemplateGroup newGroup) {
    myTemplateGroups.add(newGroup);

    int index = getIndexToInsert(myTreeRoot, newGroup.getName());
    DefaultMutableTreeNode groupNode = new CheckedTreeNode(newGroup);
    myTreeRoot.insert(groupNode, index);
    ((DefaultTreeModel)myTree.getModel()).nodesWereInserted(myTreeRoot, new int[]{index});
  }

  private int getIndexToInsert(DefaultMutableTreeNode parent, String key) {
    if (parent.getChildCount() == 0) return 0;

    int res = 0;
    for (DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getFirstChild();
         child != null;
         child = (DefaultMutableTreeNode)parent.getChildAfter(child)) {
      Object o = child.getUserObject();
      String key1 = o instanceof TemplateImpl ? ((TemplateImpl)o).getKey() : ((TemplateGroup)o).getName();
      if (key1.compareTo(key) > 0) return res;
      res++;
    }
    return res;
  }

  private void setSelectedNode(DefaultMutableTreeNode node) {
    JTree tree = myTree;
    TreePath path = new TreePath(node.getPath());
    tree.expandPath(path.getParentPath());
    int row = tree.getRowForPath(path);
    myTree.setSelectionInterval(row, row);
    //myTree.scrollRectToVisible(myTree.getR(row, 0, true));  TODO
  }

  private void removeTemplateAt(int row) {
    JTree tree = myTree;
    TreePath path = tree.getPathForRow(row);
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    LOG.assertTrue(node.getUserObject() instanceof TemplateImpl);

    TemplateImpl template = (TemplateImpl)node.getUserObject();
    getTemplateGroup(template.getGroupName()).removeElement(template);

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
    TreePath treePathToSelect = (parent.getChildAfter(node) != null || parent.getChildCount() == 1 ?
                            tree.getPathForRow(row + 1) :
                            tree.getPathForRow(row - 1));
    DefaultMutableTreeNode toSelect = treePathToSelect != null ? (DefaultMutableTreeNode)treePathToSelect.getLastPathComponent() : null;

    removeNodeFromParent(node);
    if (parent.getChildCount() == 0) {
      myTemplateGroups.remove(parent.getUserObject());
      removeNodeFromParent(parent);
    }
    if (toSelect != null) {
      setSelectedNode(toSelect);
    }
  }

  private void removeNodeFromParent(DefaultMutableTreeNode node) {
    TreeNode parent = node.getParent();
    int idx = parent.getIndex(node);
    node.removeFromParent();

    ((DefaultTreeModel)myTree.getModel()).nodesWereRemoved(parent, new int[]{idx}, new TreeNode[]{node});
  }

  private void initTemplates(List<TemplateGroup> groups, String lastSelectedKey) {
    myTreeRoot.removeAllChildren();
    myTemplateGroups.clear();
    for (TemplateGroup group : groups) {
      myTemplateGroups.add((TemplateGroup)group.copy());
    }

    DefaultMutableTreeNode nodeToSelect = null;
    for (TemplateGroup group : myTemplateGroups) {
      CheckedTreeNode groupNode = new CheckedTreeNode(group);
      List<TemplateImpl> templates = new ArrayList<TemplateImpl>(group.getElements());
      Collections.sort(templates, new Comparator<TemplateImpl>(){
        public int compare(final TemplateImpl o1, final TemplateImpl o2) {
          return o1.getKey().compareTo(o2.getKey());
        }
      });
      for (final Object groupTemplate : templates) {
        TemplateImpl template = (TemplateImpl)groupTemplate;
        CheckedTreeNode node = new CheckedTreeNode(template);
        node.setChecked(!template.isDeactivated());
        groupNode.add(node);

        if (lastSelectedKey != null && lastSelectedKey.equals(template.getKey())) {
          nodeToSelect = node;
        }
      }
      myTreeRoot.add(groupNode);

    }

    ((DefaultTreeModel)myTree.getModel()).nodeStructureChanged(myTreeRoot);

    if (nodeToSelect != null) {
      JTree tree = myTree;
      TreePath path = new TreePath(nodeToSelect.getPath());
      tree.expandPath(path.getParentPath());
      int rowToSelect = tree.getRowForPath(path);
      myTree.setSelectionInterval(rowToSelect, rowToSelect);
      /*       TODO
      final Rectangle rect = myTreeTable.getCellRect(rowToSelect, 0, true);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myTreeTable.scrollRectToVisible(rect);
            }
          });*/
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

}

