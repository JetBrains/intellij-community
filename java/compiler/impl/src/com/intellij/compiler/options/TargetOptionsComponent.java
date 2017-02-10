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
package com.intellij.compiler.options;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/9/12
 */
public class TargetOptionsComponent extends JPanel {
  private static final String[] KNOWN_TARGETS;
  private static final String COMPILER_DEFAULT = "Same as language level";

  private ComboBox myCbProjectTargetLevel;
  private JBTable myTable;
  private final Project myProject;
  
  static {
    List<String> targets = new ArrayList<>();
    targets.add("1.1");
    targets.add("1.2");
    for (LanguageLevel level : LanguageLevel.values()) {
      final String target = level.getCompilerComplianceDefaultOption();
      if (!StringUtil.isEmptyOrSpaces(target)) {
        targets.add(target);
      }
    }
    KNOWN_TARGETS = targets.toArray(new String[targets.size()]);
  }
  public TargetOptionsComponent(Project project) {
    super(new GridBagLayout());
    myProject = project;
    //setBorder(BorderFactory.createTitledBorder("Bytecode target level"));
    myCbProjectTargetLevel = createTargetOptionsCombo();

    myTable = new JBTable(new TargetLevelTableModel());
    myTable.setRowHeight(22);
    myTable.getEmptyText().setText("All modules will be compiled with project bytecode version");

    final TableColumn moduleColumn = myTable.getColumnModel().getColumn(0);
    moduleColumn.setHeaderValue("Module");
    moduleColumn.setCellRenderer(new ModuleCellRenderer());

    final TableColumn targetLevelColumn = myTable.getColumnModel().getColumn(1);
    final String columnTitle = "Target bytecode version";
    targetLevelColumn.setHeaderValue(columnTitle);
    targetLevelColumn.setCellEditor(new TargetLevelCellEditor());
    targetLevelColumn.setCellRenderer(new TargetLevelCellRenderer());
    final int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(columnTitle) + 10;
    targetLevelColumn.setPreferredWidth(width);
    targetLevelColumn.setMinWidth(width);
    targetLevelColumn.setMaxWidth(width);

    new TableSpeedSearch(myTable);

    add(new JLabel("Project bytecode version: "),
        constraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NONE));
    add(myCbProjectTargetLevel, constraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NONE));
    add(new JLabel("Per-module bytecode version:"), constraints(0, 1, 2, 1, 1.0, 0.0, GridBagConstraints.NONE));
    final JPanel tableComp = ToolbarDecorator.createDecorator(myTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          addModules();
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          removeSelectedModules();
        }
      }).createPanel();

    tableComp.setPreferredSize(new Dimension(myTable.getWidth(), 150));
    add(tableComp, constraints(0, 2, 2, 1, 1.0, 1.0, GridBagConstraints.BOTH));
  }

  private void removeSelectedModules() {
    final int[] rows = myTable.getSelectedRows();
    if (rows.length > 0) {
      TableUtil.removeSelectedItems(myTable);
    }
  }

  private void addModules() {
    final TargetLevelTableModel model = (TargetLevelTableModel)myTable.getModel();
    final List<Module> items = new ArrayList<>(Arrays.asList(ModuleManager.getInstance(myProject).getModules()));
    Set<Module> alreadyAdded = new HashSet<>();
    for (TargetLevelTableModel.Item item : model.getItems()) {
      alreadyAdded.add(item.module);
    }
    for (Iterator<Module> it = items.iterator(); it.hasNext(); ) {
      Module module = it.next();
      if (alreadyAdded.contains(module)) {
        it.remove();
      }
    }
    Collections.sort(items, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    final ChooseModulesDialog chooser = new ChooseModulesDialog(this, items, "Choose module");
    chooser.show();
    final List<Module> elements = chooser.getChosenElements();
    if (!elements.isEmpty()) {
      model.addItems(elements);
      int i = model.getModuleRow(elements.get(0));
      if (i != -1) {
        TableUtil.selectRows(myTable, new int[]{i});
        TableUtil.scrollSelectionToVisible(myTable);
      }
    }
  }

  public void setProjectBytecodeTargetLevel(String level) {
    myCbProjectTargetLevel.setSelectedItem(level == null? "" : level);
  }

  @Nullable
  public String getProjectBytecodeTarget() {
    final String item = ((String)myCbProjectTargetLevel.getSelectedItem()).trim();
    return "".equals(item)? null : item;
  }

  public Map<String, String> getModulesBytecodeTargetMap() {
    TargetLevelTableModel model = (TargetLevelTableModel)myTable.getModel();
    final Map<String, String> map = new HashMap<>();
    for (TargetLevelTableModel.Item item : model.getItems()) {
      map.put(item.module.getName(), item.targetLevel);
    }
    return map;
  }

  public void setModuleTargetLevels(Map<String, String> moduleLevels) {
    final Map<Module, String> map = new HashMap<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final String target = moduleLevels.get(module.getName());
      if (target != null) {
        map.put(module, target);
      }
    }
    ((TargetLevelTableModel)myTable.getModel()).setItems(map);
  }

  private static GridBagConstraints constraints(final int gridx, final int gridy, final int gridwidth, final int gridheight, final double weightx, final double weighty, final int fill) {
    return new GridBagConstraints(gridx, gridy, gridwidth, gridheight, weightx, weighty, GridBagConstraints.WEST, fill, JBUI.insets(5, 5, 0, 0), 0, 0);
  }

  private static final class TargetLevelTableModel extends AbstractTableModel implements ItemRemovable{
    private final List<Item> myItems = new ArrayList<>();
    @Override
    public int getRowCount() {
      return myItems.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      final Item item = myItems.get(rowIndex);
      return columnIndex == 0? item.module : item.targetLevel;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      final Item item = myItems.get(rowIndex);
      item.targetLevel = ((String)aValue).trim();
      fireTableCellUpdated(rowIndex, columnIndex);
    }

    //public void addItem(Module module)  {
    //  final int size = myItems.size();
    //  myItems.add(new Item(module.getName()));
    //  fireTableRowsInserted(size, size);
    //}

    public void addItems(Collection<Module> modules)  {
      for (Module module : modules) {
        myItems.add(new Item(module));
      }
      sorItems();
      fireTableDataChanged();
    }

    private void sorItems() {
      Collections.sort(myItems, (o1, o2) -> o1.module.getName().compareTo(o2.module.getName()));
    }

    public List<Item> getItems() {
      return myItems;
    }

    @Override
    public void removeRow(int idx) {
      myItems.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    public void setItems(Map<Module, String> items) {
      myItems.clear();
      for (Map.Entry<Module, String> entry : items.entrySet()) {
        myItems.add(new Item(entry.getKey(), entry.getValue()));
      }
      sorItems();
      fireTableDataChanged();
    }

    public int getModuleRow(Module module) {
      for (int i = 0; i < myItems.size(); i++) {
        if (myItems.get(i).module.equals(module)) {
          return i;
        }
      }
      return -1;
    }

    private static final class Item {
      final Module module;
      String targetLevel = "";

      Item(Module module) {
        this.module = module;
      }

      Item(Module module, String targetLevel) {
        this.module = module;
        this.targetLevel = targetLevel;
      }
    }
  }

  private static final class TargetLevelComboboxModel extends AbstractListModel implements ComboBoxModel{

    private final List<String> myOptions = new ArrayList<>();
    private String mySelectedItem = "";

    TargetLevelComboboxModel() {
      for (int i = KNOWN_TARGETS.length - 1; i >= 0; i--) {
        myOptions.add(KNOWN_TARGETS[i]);
      }
    }

    @Override
    public int getSize() {
      return myOptions.size();
    }

    @Override
    public void setSelectedItem(Object anItem) {
      mySelectedItem = toModelItem((String)anItem);
      fireContentsChanged(this, 0, myOptions.size());
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public Object getElementAt(int index) {
      return myOptions.get(index);
    }

    private String toModelItem(String item) {
      item = item.trim();
      for (String option : myOptions) {
        if (option.equals(item)) {
          return option;
        }
      }
      return item;
    }
  }

  private static ComboBox createTargetOptionsCombo() {
    final ComboBox combo = new ComboBox(new TargetLevelComboboxModel());
    //combo.setRenderer(new DefaultListCellRenderer() {
    //  @Override
    //  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    //    try {
    //      return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    //    }
    //    finally {
    //      //if ("".equals(value)) {
    //      //  setText(COMPILER_DEFAULT);
    //      //}
    //    }
    //  }
    //});
    combo.setEditable(true);
    combo.setEditor(new BasicComboBoxEditor() {
      @Override
      protected JTextField createEditorComponent() {
        HintTextField editor = new HintTextField(COMPILER_DEFAULT, 12);
        editor.setBorder(null);
        return editor;
      }
    });
    return combo;
  }

  private static class ModuleCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      try {
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
      finally {
        final Module module = (Module)value;
        if (module != null) {
          setText(module.getName());
          setIcon(ModuleType.get(module).getIcon());
        }
        else {
          setText("");
          setIcon(null);
        }
      }
    }
  }

  private static class TargetLevelCellEditor extends DefaultCellEditor {
    private TargetLevelCellEditor() {
      super(createTargetOptionsCombo());
      setClickCountToStart(0);
    }
  }

  private static class TargetLevelCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel) {
        final JLabel comp = (JLabel)component;
        comp.setHorizontalAlignment(SwingConstants.CENTER);
        if ("".equals(value)) {
          comp.setForeground(JBColor.GRAY);
          comp.setText(COMPILER_DEFAULT);
        }
        else {
          comp.setForeground(table.getForeground());
        }
      }
      return component;
    }
  }

  static class HintTextField extends JTextField {
    private final char[] myHint;

    public HintTextField(final String hint) {
      this(hint, 0);
    }

    public HintTextField(final String hint, final int columns) {
      super(hint, columns);
      myHint = hint.toCharArray();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      final boolean isFocused = isFocusOwner();
      if (!isFocused && getText().isEmpty()) {
        final Color oldColor = g.getColor();
        final Font oldFont = g.getFont();
        try {
          g.setColor(JBColor.GRAY);
          //g.setFont(oldFont.deriveFont(Font.ITALIC));
          final FontMetrics metrics = g.getFontMetrics();
          int x = Math.abs(getWidth() - metrics.charsWidth(myHint, 0, myHint.length)) / 2;
          int y = Math.abs(getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
          g.drawChars(myHint, 0, myHint.length, x, y);
        }
        finally {
          g.setColor(oldColor);
          g.setFont(oldFont);
        }
      }
    }
  }
}
