/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableCellRenderer;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author max
 */
public abstract class OptionTableWithPreviewPanel extends MultilanguageCodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleSpacesPanel");

  @SuppressWarnings({"HardCodedStringLiteral"})
  public final ColumnInfo TITLE = new ColumnInfo("TITLE") {
    public Object valueOf(Object o) {
      if (o instanceof MyTreeNode) {
        MyTreeNode node = (MyTreeNode)o;
        return node.getText();
      }
      return o.toString();
    }

    public Class getColumnClass() {
      return TreeTableModel.class;
    }
  };

  @SuppressWarnings({"HardCodedStringLiteral"})
  public final ColumnInfo VALUE = new ColumnInfo("VALUE") {
    private final TableCellEditor myEditor = new MyValueEditor();
    private final TableCellRenderer myRenderer = new MyValueRenderer();

    public Object valueOf(Object o) {
      if (o instanceof MyTreeNode) {
        MyTreeNode node = (MyTreeNode)o;
        return node.getValue();
      }

      return null;
    }

    public TableCellRenderer getRenderer(Object o) {
      return myRenderer;
    }

    public TableCellEditor getEditor(Object item) {
      return myEditor;
    }

    public boolean isCellEditable(Object o) {
      return (o instanceof MyTreeNode) && (((MyTreeNode)o).isEnabled());
    }

    public void setValue(Object o, Object o1) {
      MyTreeNode node = (MyTreeNode)o;
      node.setValue(o1);
    }
  };

  public final ColumnInfo[] COLUMNS = new ColumnInfo[]{TITLE, VALUE};

  private final TreeCellRenderer myTitleRenderer = new TreeCellRenderer() {
    private final JLabel myLabel = new JLabel();

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      if (value instanceof MyTreeNode) {
        MyTreeNode node = (MyTreeNode)value;
        myLabel.setText(node.getText());
        myLabel.setFont(
          myLabel.getFont().deriveFont(node.getKey().groupName == null ? Font.BOLD : Font.PLAIN));
        myLabel.setEnabled(node.isEnabled());
      }
      else {
        myLabel.setText(value.toString());
        myLabel.setFont(myLabel.getFont().deriveFont(Font.BOLD));
        myLabel.setEnabled(true);
      }

      Color foreground = selected
                         ? UIUtil.getTableSelectionForeground()
                         : UIUtil.getTableForeground();
      myLabel.setForeground(foreground);

      return myLabel;
    }
  };

  private TreeTable myTreeTable;
  private final HashMap myKeyToFieldMap = new HashMap();
  private final ArrayList<OptionKey> myKeys = new ArrayList<OptionKey>();

  private final JPanel myPanel = new JPanel();

  public OptionTableWithPreviewPanel(CodeStyleSettings settings) {
    super(settings);
  }

  @Override
  protected void init() {
    super.init();

    myPanel.setLayout(new GridBagLayout());
    initTables();

    myTreeTable = createOptionsTree(getSettings());
    myPanel.add(ScrollPaneFactory.createScrollPane(myTreeTable),
                new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(7, 7, 3, 4), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel.add(previewPanel,
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(0, 0, 0, 4), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

  }

  @Override
  protected void onLanguageChange(Language language) {
    if (myTreeTable.isEditing()) {
      myTreeTable.getCellEditor().stopCellEditing();
    }
    resetImpl(getSettings());
    myTreeTable.repaint();
  }

  @Override
  public void showAllStandardOptions() {
    for (OptionKey each : myKeys) {
      each.setEnabled(true);
    }
  }

  @Override
  public void showStandardOptions(String... optionNames) {
    for (OptionKey each : myKeys) {
      each.setEnabled(false);
      Field field = (Field)myKeyToFieldMap.get(each);
      for (String optionName : optionNames) {
        if (field.getName().equals(optionName)) {
          each.setEnabled(true);
        }
      }
    }
  }

  @Override
  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass,
                               String fieldName,
                               String title,
                               String groupName) {
    //TODO: IMPLEMENT
  }

  @Override
  public void renameStandardOption(String fieldName, String newTitle) {
    //TODO: IMPLEMENT
  }

  protected TreeTable createOptionsTree(CodeStyleSettings settings) {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

    String currentGroupName = "";
    DefaultMutableTreeNode currentGroupNode = null;

    for (OptionKey each : myKeys) {
      String group = each.groupName;

      MyTreeNode newNode = new MyTreeNode(each, each.title, settings);
      if (currentGroupNode == null || !Comparing.equal(group, currentGroupName)) {
        if (group == null) {
          currentGroupName = each.title;
          currentGroupNode = newNode;
        }
        else {
          currentGroupName = group;
          currentGroupNode = new DefaultMutableTreeNode(group);
          currentGroupNode.add(newNode);
        }
        rootNode.add(currentGroupNode);
      }
      else {
        currentGroupNode.add(newNode);
      }
    }

    ListTreeTableModel model = new ListTreeTableModel(rootNode, COLUMNS);
    TreeTable treeTable = new TreeTable(model) {
      public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
        TreeTableCellRenderer tableRenderer = super.createTableRenderer(treeTableModel);
        UIUtil.setLineStyleAngled(tableRenderer);
        tableRenderer.setRootVisible(false);
        tableRenderer.setShowsRootHandles(true);

        return tableRenderer;
      }

      public TableCellRenderer getCellRenderer(int row, int column) {
        TreePath treePath = getTree().getPathForRow(row);
        if (treePath == null) return super.getCellRenderer(row, column);

        Object node = treePath.getLastPathComponent();

        TableCellRenderer renderer = COLUMNS[column].getRenderer(node);
        return renderer == null ? super.getCellRenderer(row, column) : renderer;
      }

      public TableCellEditor getCellEditor(int row, int column) {
        TreePath treePath = getTree().getPathForRow(row);
        if (treePath == null) return super.getCellEditor(row, column);

        Object node = treePath.getLastPathComponent();
        TableCellEditor editor = COLUMNS[column].getEditor(node);
        return editor == null ? super.getCellEditor(row, column) : editor;
      }
    };

    treeTable.setRootVisible(false);

    final JTree tree = treeTable.getTree();
    tree.setCellRenderer(myTitleRenderer);
    tree.setShowsRootHandles(true);
    //myTreeTable.setRowHeight(new JComboBox(new String[]{"Sample Text"}).getPreferredSize().height);
    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    treeTable.setTableHeader(null);

    expandTree(tree);

    int maxWidth = tree.getPreferredScrollableViewportSize().width + 10;
    final TableColumn titleColumn = treeTable.getColumnModel().getColumn(0);
    titleColumn.setPreferredWidth(maxWidth);
    titleColumn.setMinWidth(maxWidth);
    titleColumn.setMaxWidth(maxWidth);
    titleColumn.setResizable(false);

    final TableColumn levelColumn = treeTable.getColumnModel().getColumn(1);
    //TODO[max]: better preffered size...
    JLabel value = new JLabel(ApplicationBundle.message("option.table.sizing.text"));
    final Dimension valueSize = value.getPreferredSize();
    levelColumn.setPreferredWidth(valueSize.width);
    levelColumn.setMaxWidth(valueSize.width);
    levelColumn.setMinWidth(valueSize.width);
    levelColumn.setResizable(false);

    treeTable.setPreferredScrollableViewportSize(new Dimension(maxWidth + valueSize.width + 10, 20));

    return treeTable;
  }

  private void expandTree(final JTree tree) {
    int oldRowCount = 0;
    do {
      int rowCount = tree.getRowCount();
      if (rowCount == oldRowCount) break;
      oldRowCount = rowCount;
      for (int i = 0; i < rowCount; i++) {
        tree.expandRow(i);
      }
    }
    while (true);
  }

  protected abstract void initTables();

  private void resetNode(TreeNode node, CodeStyleSettings settings) {
    if (node instanceof MyTreeNode) {
      ((MyTreeNode)node).reset(settings);
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      resetNode(child, settings);
    }
  }

  private void applyNode(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyTreeNode) {
      ((MyTreeNode)node).apply(settings);
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      applyNode(child, settings);
    }
  }

  private boolean isModified(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyTreeNode) {
      if (((MyTreeNode)node).isModified(settings)) return true;
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      if (isModified(child, settings)) {
        return true;
      }
    }
    return false;
  }

  protected void addOption(@NotNull String fieldName, @NotNull String title) {
    addOption(fieldName, title, null);
  }

  protected void addOption(@NotNull String fieldName, @NotNull String title, @Nullable String groupName) {
    try {
      Class styleSettingsClass = CodeStyleSettings.class;
      Field field = styleSettingsClass.getField(fieldName);
      BooleanOptionKey key = new BooleanOptionKey(title, groupName);
      myKeyToFieldMap.put(key, field);
      myKeys.add(key);
    }
    catch (NoSuchFieldException e) {
    }
    catch (SecurityException e) {
    }
  }

  protected void addOption(@NotNull String fieldName, @NotNull String title, @NotNull String[] options, @NotNull int[] values) {
    addOption(fieldName, title, null, options, values);
  }

  protected void addOption(@NotNull String fieldName, @NotNull String title, @Nullable String groupName,
                          @NotNull String[] options, @NotNull int[] values) {
    try {
      Class styleSettingsClass = CodeStyleSettings.class;
      Field field = styleSettingsClass.getField(fieldName);
      SelectionOptionKey key = new SelectionOptionKey(title, groupName, options, values);
      myKeyToFieldMap.put(key, field);
      myKeys.add(key);
    }
    catch (NoSuchFieldException e) {
    }
    catch (SecurityException e) {
    }
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    //
  }

  private static abstract class OptionKey {
    @NotNull final String title;
    @Nullable final String groupName;
    private boolean myEnabled = false;

    public OptionKey(@NotNull String title, @Nullable String groupName) {
      this.title = title;
      this.groupName = groupName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      OptionKey that = (OptionKey)o;

      if (groupName != null ? !groupName.equals(that.groupName) : that.groupName != null) return false;
      if (!title.equals(that.title)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = title.hashCode();
      result = 31 * result + (groupName != null ? groupName.hashCode() : 0);
      return result;
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    public boolean isEnabled() {
      return myEnabled;
    }
  }

  private static class BooleanOptionKey extends OptionKey {
    private BooleanOptionKey(@NotNull String title, @Nullable String groupName) {
      super(title, groupName);
    }
  }

  private static class SelectionOptionKey extends OptionKey {
    @NotNull final String[] options;
    @NotNull final int[] values;

    public SelectionOptionKey(@NotNull String title, @Nullable String groupName, @NotNull String[] options, @NotNull int[] values) {
      super(title, groupName);
      this.options = options;
      this.values = values;
    }

    @Override
    public boolean equals(Object o) {
      if (!super.equals(o)) return false;

      SelectionOptionKey that = (SelectionOptionKey)o;
      if (!Arrays.equals(options, that.options)) return false;
      if (!Arrays.equals(values, that.values)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Arrays.hashCode(options);
      result = 31 * result + Arrays.hashCode(values);
      return result;
    }
  }

  private Object getSettingsValue(Object key, final CodeStyleSettings settings) {
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(getSelectedLanguage());
    try {
      if (key instanceof BooleanOptionKey) {
        Field field = (Field)myKeyToFieldMap.get(key);
        return field.getBoolean(commonSettings) ? Boolean.TRUE : Boolean.FALSE;
      }
      else if (key instanceof SelectionOptionKey) {
        Field field = (Field)myKeyToFieldMap.get(key);
        SelectionOptionKey intKey = (SelectionOptionKey)key;
        int[] values = intKey.values;
        int value = field.getInt(commonSettings);
        for (int i = 0; i < values.length; i++) {
          if (values[i] == value) return intKey.options[i];
        }
      }
    }
    catch (IllegalAccessException e) {
    }

    return null;
  }

  public void setSettingsValue(Object key, Object value, final CodeStyleSettings settings) {
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(getSelectedLanguage());
    try {
      if (key instanceof BooleanOptionKey) {
        Field field = (Field)myKeyToFieldMap.get(key);
        field.setBoolean(commonSettings, ((Boolean)value).booleanValue());
      }
      else if (key instanceof SelectionOptionKey) {
        Field field = (Field)myKeyToFieldMap.get(key);
        SelectionOptionKey intKey = (SelectionOptionKey)key;
        int[] values = intKey.values;
        for (int i = 0; i < values.length; i++) {
          if (intKey.options[i].equals(value)) {
            field.setInt(commonSettings, values[i]);
            return;
          }
        }
      }
    }
    catch (IllegalAccessException e) {
    }
  }

  private class MyTreeNode extends DefaultMutableTreeNode {
    private final OptionKey myKey;
    private final String myText;
    private Object myValue;

    public MyTreeNode(OptionKey key, String text, CodeStyleSettings settings) {
      myKey = key;
      myText = text;
      myValue = getSettingsValue(key, settings);
    }

    public OptionKey getKey() {
      return myKey;
    }

    public String getText() {
      return myText;
    }

    public Object getValue() {
      return myValue;
    }

    public void setValue(Object value) {
      myValue = value;
    }

    public void reset(CodeStyleSettings settings) {
      setValue(getSettingsValue(myKey, settings));
    }

    public boolean isModified(final CodeStyleSettings settings) {
      return !myValue.equals(getSettingsValue(myKey, settings));
    }

    public void apply(final CodeStyleSettings settings) {
      setSettingsValue(myKey, myValue, settings);
    }

    public boolean isEnabled() {
      return myKey.isEnabled();
    }
  }

  private class MyValueRenderer implements TableCellRenderer {
    private final JLabel myComboBox = new JLabel();
    private final JCheckBox myCheckBox = new JCheckBox();
    private final JPanel myEmptyLabel = new JPanel();

    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      boolean isEnabled = true;
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)((TreeTable)table).getTree().
        getPathForRow(row).getLastPathComponent();
      if (node instanceof MyTreeNode)  {
        isEnabled = ((MyTreeNode)node).isEnabled();
      }

      Color background = table.getBackground();
      if (value instanceof Boolean) {
        myCheckBox.setSelected(((Boolean)value).booleanValue());
        myCheckBox.setBackground(background);
        myCheckBox.setEnabled(isEnabled);
        return myCheckBox;
      }
      else if (value instanceof String) {
        /*
        myComboBox.removeAllItems();
        myComboBox.addItem(value);
        */
        myComboBox.setText((String)value);
        myComboBox.setBackground(background);
        myComboBox.setEnabled(isEnabled);
        return myComboBox;
      }

      myCheckBox.putClientProperty("JComponent.sizeVariant", "small");
      myComboBox.putClientProperty("JComponent.sizeVariant", "small");

      myEmptyLabel.setBackground(background);
      return myEmptyLabel;
    }
  }

  private class MyValueEditor extends AbstractTableCellEditor {
    private final JComboBox myComboBox = new JComboBox();
    private final JCheckBox myCheckBox = new JCheckBox();
    private Component myCurrentEditor = null;
    private MyTreeNode myCurrentNode = null;

    public MyValueEditor() {
      ActionListener synchronizer = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myCurrentNode != null) {
            myCurrentNode.setValue(getCellEditorValue());
          }
        }
      };
      myComboBox.addActionListener(synchronizer);
      myCheckBox.addActionListener(synchronizer);

      myComboBox.putClientProperty("JComponent.sizeVariant", "small");
      myCheckBox.putClientProperty("JComponent.sizeVariant", "small");
    }

    public Object getCellEditorValue() {
      if (myCurrentEditor == myComboBox) {
        return myComboBox.getSelectedItem();
      }
      else if (myCurrentEditor == myCheckBox) {
        return myCheckBox.isSelected() ? Boolean.TRUE : Boolean.FALSE;
      }

      return null;
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      final DefaultMutableTreeNode defaultNode = (DefaultMutableTreeNode)((TreeTable)table).getTree().
        getPathForRow(row).getLastPathComponent();
      myCurrentEditor = null;
      myCurrentNode = null;
      if (defaultNode instanceof MyTreeNode) {
        MyTreeNode node = (MyTreeNode)defaultNode;
        if (node.getKey() instanceof BooleanOptionKey) {
          myCurrentEditor = myCheckBox;
          myCheckBox.setSelected(node.getValue() == Boolean.TRUE);
          myCheckBox.setEnabled(node.isEnabled());
        }
        else {
          myCurrentEditor = myComboBox;
          myComboBox.removeAllItems();
          SelectionOptionKey key = (SelectionOptionKey)node.getKey();
          String[] values = key.options;
          for (int i = 0; i < values.length; i++) {
            myComboBox.addItem(values[i]);
          }
          myComboBox.setSelectedItem(node.getValue());
          myComboBox.setEnabled(node.isEnabled());
        }
        myCurrentNode = node;
      }

      myCurrentEditor.setBackground(table.getBackground());

      return myCurrentEditor;
    }
  }

  public void apply(CodeStyleSettings settings) {
    TreeModel treeModel = myTreeTable.getTree().getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    applyNode(root, settings);
  }

  public boolean isModified(CodeStyleSettings settings) {
    TreeModel treeModel = myTreeTable.getTree().getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    if (isModified(root, settings)) {
      return true;
    }
    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    TreeModel treeModel = myTreeTable.getTree().getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    resetNode(root, settings);
  }

}