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
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.*;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CodeStyleImportsPanel extends JPanel {
  private JCheckBox myCbUseFQClassNames;
  private JCheckBox myCbUseFQClassNamesInJavaDoc;
  private JCheckBox myCbUseSingleClassImports;
  private JCheckBox myCbInsertInnerClassImports;
  private JTextField myClassCountField;
  private JTextField myNamesCountField;
  private final PackageEntryTable myImportLayoutList = new PackageEntryTable();
  private final PackageEntryTable myPackageList = new PackageEntryTable();

  private Table myImportLayoutTable;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;
  private JButton myRemovePackageFromImportLayoutButton;
  private JButton myRemovePackageFromPackagesButton;
  private Table myPackageTable;
  private final CodeStyleSettings mySettings;
  private JRadioButton myJspImportCommaSeparated;
  private JRadioButton myJspOneImportPerDirective;

  private JPanel myGeneralPanel;
  private JPanel myJSPPanel;
  private JPanel myPackagesPanel;
  private JPanel myImportsLayoutPanel;
  private JPanel myWholePanel;
  private JCheckBox myCbLayoutStaticImportsSeparately;

  public CodeStyleImportsPanel(CodeStyleSettings settings){
    mySettings = settings;
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
    add(myWholePanel, BorderLayout.CENTER);

    myGeneralPanel.add(createGeneralOptionsPanel(), BorderLayout.CENTER);
    myJSPPanel.add(createJspImportLayoutPanel(), BorderLayout.CENTER);
    myImportsLayoutPanel.add(createImportLayoutPanel(), BorderLayout.NORTH);
    myPackagesPanel.add(createPackagesPanel(), BorderLayout.NORTH);
  }

  private JPanel createJspImportLayoutPanel() {
    ButtonGroup buttonGroup = new ButtonGroup();
    myJspImportCommaSeparated = new JRadioButton(ApplicationBundle.message("radio.prefer.comma.separated.import.list"));
    myJspOneImportPerDirective = new JRadioButton(ApplicationBundle.message("radio.prefer.one.import.statement.per.page.directive"));
    buttonGroup.add(myJspImportCommaSeparated);
    buttonGroup.add(myJspOneImportPerDirective);
    JPanel btnPanel = new JPanel(new BorderLayout());
    btnPanel.add(myJspImportCommaSeparated, BorderLayout.NORTH);
    btnPanel.add(myJspOneImportPerDirective, BorderLayout.CENTER);

    //noinspection HardCodedStringLiteral
    final MultiLineLabel commaSeparatedLabel = new MultiLineLabel("<% page import=\"com.company.Boo, \n" +
                                                                  "                 com.company.Far\"%>");
    //noinspection HardCodedStringLiteral
    final MultiLineLabel oneImportPerDirectiveLabel = new MultiLineLabel("<% page import=\"com.company.Boo\"%>\n" +
                                                                         "<% page import=\"com.company.Far\"%>");
    final JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.preview")));

    JPanel resultPanel = new JPanel(new BorderLayout());
    resultPanel.add(btnPanel, BorderLayout.NORTH);
    resultPanel.add(labelPanel, BorderLayout.CENTER);
    resultPanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.jsp.imports.layout")));


    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean isComma = myJspImportCommaSeparated.isSelected();
        labelPanel.removeAll();
        labelPanel.add(isComma ? commaSeparatedLabel : oneImportPerDirectiveLabel, BorderLayout.CENTER);
        labelPanel.repaint();
        labelPanel.revalidate();
      }
    };
    myJspImportCommaSeparated.addActionListener(actionListener);
    myJspOneImportPerDirective.addActionListener(actionListener);
    return resultPanel;
  }

  private JPanel createGeneralOptionsPanel() {
    OptionGroup group = new OptionGroup(ApplicationBundle.message("title.general"));
    myCbUseSingleClassImports = new JCheckBox(ApplicationBundle.message("checkbox.use.single.class.import"));
    group.add(myCbUseSingleClassImports);

    myCbUseFQClassNames = new JCheckBox(ApplicationBundle.message("checkbox.use.fully.qualified.class.names"));
    group.add(myCbUseFQClassNames);

    myCbInsertInnerClassImports = new JCheckBox(ApplicationBundle.message("checkbox.insert.imports.for.inner.classes"));
    group.add(myCbInsertInnerClassImports);

    myCbUseFQClassNamesInJavaDoc = new JCheckBox(ApplicationBundle.message("checkbox.use.fully.qualified.class.names.in.javadoc"));
    group.add(myCbUseFQClassNamesInJavaDoc);

    myClassCountField = new JTextField(3);
    myNamesCountField = new JTextField(3);
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(new JLabel(ApplicationBundle.message("editbox.class.count.to.use.import.with.star")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
    panel.add(myClassCountField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 1, 0, 0), 0, 0));
    panel.add(new JLabel(ApplicationBundle.message("editbox.names.count.to.use.static.import.with.star")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
    panel.add(myNamesCountField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 1, 0, 0), 0, 0));

    group.add(panel);
    return group.createPanel();
  }

  private JPanel createPackagesPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.packages.to.use.import.with")));

    panel.add(createPackagesTable(), BorderLayout.CENTER);
    panel.add(createPackagesButtonsPanel(), BorderLayout.EAST);
    panel.setPreferredSize(new Dimension(-1, 200));
    return panel;
  }

  private JPanel createImportLayoutPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.import.layout")));
    myCbLayoutStaticImportsSeparately = new JCheckBox("Layout static imports separately");

    myCbLayoutStaticImportsSeparately.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e) {
        if (areStaticImportsEnabled()) {
          boolean found = false;
          for (int i=myImportLayoutList.getEntryCount()-1; i>=0; i--) {
            PackageEntry entry = myImportLayoutList.getEntryAt(i);
            if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
              found = true;
              break;
            }
          }
          if (!found) {
            int index = myImportLayoutList.getEntryCount();
            if (index != 0 && myImportLayoutList.getEntryAt(index-1) != PackageEntry.BLANK_LINE_ENTRY) {
              myImportLayoutList.addEntry(PackageEntry.BLANK_LINE_ENTRY);
            }
            myImportLayoutList.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
          }
        }
        else {
          for (int i=myImportLayoutList.getEntryCount()-1; i>=0; i--) {
            PackageEntry entry = myImportLayoutList.getEntryAt(i);
            if (entry.isStatic()) {
              myImportLayoutList.removeEntryAt(i);
            }
          }
        }
        refreshTable(myImportLayoutTable, myImportLayoutList);
        refreshTable(myPackageTable, myPackageList);
      }
    });
    panel.add(myCbLayoutStaticImportsSeparately, BorderLayout.NORTH);
    panel.add(createImportLayoutTable(), BorderLayout.CENTER);
    panel.add(createImportLayoutButtonsPanel(), BorderLayout.EAST);
    panel.setPreferredSize(new Dimension(-1, 200));
    return panel;
  }

  private void refreshTable(final Table table, final PackageEntryTable packageTable) {
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    table.createDefaultColumnsFromModel();
    model.fireTableDataChanged();
    resizeColumns(packageTable, table);
  }

  private boolean areStaticImportsEnabled() {
    return myCbLayoutStaticImportsSeparately.isSelected();
  }

  private JPanel createImportLayoutButtonsPanel() {
    JPanel tableButtonsPanel = new JPanel(new VerticalFlowLayout());

    JButton addPackageToImportLayoutButton = new JButton(ApplicationBundle.message("button.add.package"));
    tableButtonsPanel.add(addPackageToImportLayoutButton);

    JButton addBlankLineButton = new JButton(ApplicationBundle.message("button.add.blank"));
    tableButtonsPanel.add(addBlankLineButton);

    myMoveUpButton = new JButton(ApplicationBundle.message("button.move.up"));
    tableButtonsPanel.add(myMoveUpButton);

    myMoveDownButton = new JButton(ApplicationBundle.message("button.move.down"));
    tableButtonsPanel.add(myMoveDownButton);

    myRemovePackageFromImportLayoutButton = new JButton(ApplicationBundle.message("button.remove"));
    tableButtonsPanel.add(myRemovePackageFromImportLayoutButton);

    addPackageToImportLayoutButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addPackageToImportLayouts();
        }
      }
    );

    addBlankLineButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addBlankLine();
        }
      }
    );

    myRemovePackageFromImportLayoutButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          removeEntryFromImportLayouts();
        }
      }
    );

    myMoveUpButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          moveRowUp();
        }
      }
    );

    myMoveDownButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          moveRowDown();
        }
      }
    );

    return tableButtonsPanel;
  }

  private JPanel createPackagesButtonsPanel() {
    JPanel tableButtonsPanel = new JPanel(new VerticalFlowLayout());
    tableButtonsPanel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));

    JButton addPackageToPackagesButton = new JButton(ApplicationBundle.message("button.add.package.p"));
    tableButtonsPanel.add(addPackageToPackagesButton);

    myRemovePackageFromPackagesButton = new JButton(ApplicationBundle.message("button.remove.r"));
    tableButtonsPanel.add(myRemovePackageFromPackagesButton);

    addPackageToPackagesButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addPackageToPackages();
        }
      }
    );

    myRemovePackageFromPackagesButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          removeEntryFromPackages();
        }
      }
    );

    return tableButtonsPanel;
  }

  private void addPackageToImportLayouts() {
    int selected = myImportLayoutTable.getSelectedRow() + 1;
    if(selected < 0) {
      selected = myImportLayoutList.getEntryCount();
    }
    PackageEntry entry = new PackageEntry(false,"", true);
    myImportLayoutList.insertEntryAt(entry, selected);
    refreshTableModel(selected, myImportLayoutTable);
  }

  private static void refreshTableModel(int selectedRow, Table table) {
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    model.fireTableRowsInserted(selectedRow, selectedRow);
    table.setRowSelectionInterval(selectedRow, selectedRow);
    TableUtil.editCellAt(table, selectedRow, 0);
    Component editorComp = table.getEditorComponent();
    if(editorComp != null) {
      editorComp.requestFocus();
    }
  }

  private void addPackageToPackages() {
    int selected = myPackageTable.getSelectedRow() + 1;
    if(selected < 0) {
      selected = myPackageList.getEntryCount();
    }
    PackageEntry entry = new PackageEntry(false,"", true);
    myPackageList.insertEntryAt(entry, selected);
    refreshTableModel(selected, myPackageTable);
  }

  private void addBlankLine() {
    int selected = myImportLayoutTable.getSelectedRow() + 1;
    if(selected < 0) {
      selected = myImportLayoutList.getEntryCount();
    }
    myImportLayoutList.insertEntryAt(PackageEntry.BLANK_LINE_ENTRY, selected);
    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsInserted(selected, selected);
    myImportLayoutTable.setRowSelectionInterval(selected, selected);
  }

  private void removeEntryFromImportLayouts() {
    int selected = myImportLayoutTable.getSelectedRow();
    if(selected < 0)
      return;
    PackageEntry entry = myImportLayoutList.getEntryAt(selected);
    if(entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY || entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY) {
      return;
    }
    TableUtil.stopEditing(myImportLayoutTable);
    myImportLayoutList.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if(selected >= myImportLayoutList.getEntryCount()) {
      selected --;
    }
    if(selected >= 0) {
      myImportLayoutTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void removeEntryFromPackages() {
    int selected = myPackageTable.getSelectedRow();
    if(selected < 0) return;
    TableUtil.stopEditing(myPackageTable);
    myPackageList.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myPackageTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if(selected >= myPackageList.getEntryCount()) {
      selected --;
    }
    if(selected >= 0) {
      myPackageTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveRowUp() {
    int selected = myImportLayoutTable.getSelectedRow();
    if(selected < 1) {
      return;
    }
    TableUtil.stopEditing(myImportLayoutTable);
    PackageEntry entry = myImportLayoutList.getEntryAt(selected);
    PackageEntry previousEntry = myImportLayoutList.getEntryAt(selected-1);
    myImportLayoutList.setEntryAt(previousEntry, selected);
    myImportLayoutList.setEntryAt(entry, selected-1);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsUpdated(selected-1, selected);
    myImportLayoutTable.setRowSelectionInterval(selected-1, selected-1);
  }

  private void moveRowDown() {
    int selected = myImportLayoutTable.getSelectedRow();
    if(selected >= myImportLayoutList.getEntryCount()-1) {
      return;
    }
    TableUtil.stopEditing(myImportLayoutTable);
    PackageEntry entry = myImportLayoutList.getEntryAt(selected);
    PackageEntry nextEntry = myImportLayoutList.getEntryAt(selected+1);
    myImportLayoutList.setEntryAt(nextEntry, selected);
    myImportLayoutList.setEntryAt(entry, selected+1);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsUpdated(selected, selected+1);
    myImportLayoutTable.setRowSelectionInterval(selected+1, selected+1);
  }

  private JComponent createPackagesTable() {
    myPackageTable = createTableForPackageEntries(myPackageList);
    return ScrollPaneFactory.createScrollPane(myPackageTable);
  }

  private Table createTableForPackageEntries(final PackageEntryTable packageTable) {
    final String[] names = {
      ApplicationBundle.message("listbox.import.package"),
      ApplicationBundle.message("listbox.import.with.subpackages"),
    };
    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      public int getColumnCount() {
        return names.length + (areStaticImportsEnabled()?1:0);
      }

      public int getRowCount() {
        return packageTable.getEntryCount();
      }

      public Object getValueAt(int row, int col) {
        PackageEntry entry = packageTable.getEntryAt(row);
        if (entry == null || !isCellEditable(row, col)) return null;
        col += areStaticImportsEnabled() ? 0 : 1;
        if(col == 0) {
          return entry.isStatic();
        }
        if(col == 1) {
          return entry.getPackageName();
        }
        if(col == 2) {
          return entry.isWithSubpackages() ? Boolean.TRUE : Boolean.FALSE;
        }
        throw new IllegalArgumentException(String.valueOf(col));
      }

      public String getColumnName(int column) {
        if (areStaticImportsEnabled() && column == 0) return "Static";
        column -= areStaticImportsEnabled() ? 1 : 0;
        return names[column];
      }

      public Class getColumnClass(int col) {
        col += areStaticImportsEnabled() ? 0 : 1;
        if(col == 0) {
          return Boolean.class;
        }
        if(col == 1) {
          return String.class;
        }
        if(col == 2) {
          return Boolean.class;
        }
        throw new IllegalArgumentException(String.valueOf(col));
      }

      public boolean isCellEditable(int row, int col) {
        PackageEntry packageEntry = packageTable.getEntryAt(row);
        return !packageEntry.isSpecial();
      }

      public void setValueAt(Object aValue, int row, int col) {
        PackageEntry packageEntry = packageTable.getEntryAt(row);
        col += areStaticImportsEnabled() ? 0 : 1;
        if(col == 0) {
          PackageEntry newPackageEntry = new PackageEntry((Boolean)aValue, packageEntry.getPackageName(), packageEntry.isWithSubpackages());
          packageTable.setEntryAt(newPackageEntry, row);
        }
        else if(col == 1) {
          PackageEntry newPackageEntry = new PackageEntry(packageEntry.isStatic(), ((String)aValue).trim(), packageEntry.isWithSubpackages());
          packageTable.setEntryAt(newPackageEntry, row);
        }
        else if(col == 2) {
          PackageEntry newPackageEntry = new PackageEntry(packageEntry.isStatic(), packageEntry.getPackageName(), ((Boolean)aValue).booleanValue());
          packageTable.setEntryAt(newPackageEntry, row);
        }
        else {
          throw new IllegalArgumentException(String.valueOf(col));
        }
      }
    };

    // Create the table
    final Table result = new Table(dataModel);
    result.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    resizeColumns(packageTable, result);

    TableCellEditor editor = result.getDefaultEditor(String.class);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }

    TableCellEditor beditor = result.getDefaultEditor(Boolean.class);
    beditor.addCellEditorListener(new CellEditorListener() {
      public void editingStopped(ChangeEvent e) {
        if (areStaticImportsEnabled()) {
          result.repaint(); // add/remove static keyword
        }
      }

      public void editingCanceled(ChangeEvent e) {
      }
    });

    result.getSelectionModel().addListSelectionListener(
      new ListSelectionListener(){
        public void valueChanged(ListSelectionEvent e){
          updateButtons();
        }
      }
    );

    return result;
  }

  private void resizeColumns(final PackageEntryTable packageTable, Table result) {
    ColoredTableCellRenderer packageRenderer = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        PackageEntry entry = packageTable.getEntryAt(row);

        if (entry == PackageEntry.BLANK_LINE_ENTRY) {
          append("                                               <blank line>", SimpleTextAttributes.LINK_ATTRIBUTES);
        }
        else {
          TextAttributes attributes = SyntaxHighlighterColors.KEYWORD.getDefaultAttributes();
          append("import", SimpleTextAttributes.fromTextAttributes(attributes));
          if (entry.isStatic()) {
            append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append("static", SimpleTextAttributes.fromTextAttributes(attributes));
          }
          append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

          if (entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY || entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
            append("all other imports", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            append(entry.getPackageName() + ".*", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      }
    };
    if (areStaticImportsEnabled()) {
      fixColumnWidthToHeader(result, 0);
      fixColumnWidthToHeader(result, 2);
      result.getColumnModel().getColumn(1).setCellRenderer(packageRenderer);
      result.getColumnModel().getColumn(0).setCellRenderer(new BooleanTableCellRenderer());
      result.getColumnModel().getColumn(2).setCellRenderer(new BooleanTableCellRenderer());
    }
    else {
      fixColumnWidthToHeader(result, 1);
      result.getColumnModel().getColumn(0).setCellRenderer(packageRenderer);
      result.getColumnModel().getColumn(1).setCellRenderer(new BooleanTableCellRenderer());
    }
  }

  private static void fixColumnWidthToHeader(Table result, int columnIdx) {
    final TableColumn column = result.getColumnModel().getColumn(columnIdx);
    final int width = result.getTableHeader().getFontMetrics(result.getTableHeader().getFont()).stringWidth(result.getColumnName(columnIdx)) + 6;
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  private void updateButtons(){
    int selectedImport = myImportLayoutTable.getSelectedRow();
    myMoveUpButton.setEnabled(selectedImport >= 1);
    myMoveDownButton.setEnabled(selectedImport < myImportLayoutTable.getRowCount()-1);
    PackageEntry entry = selectedImport < 0 ? null : myImportLayoutList.getEntryAt(selectedImport);
    boolean canRemove =  entry != null && entry != PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY && entry != PackageEntry.ALL_OTHER_IMPORTS_ENTRY;
    myRemovePackageFromImportLayoutButton.setEnabled(canRemove);

    int selectedPackage = myPackageTable.getSelectedRow();
    myRemovePackageFromPackagesButton.setEnabled(selectedPackage >= 0);
  }

  private JComponent createImportLayoutTable() {
    myImportLayoutTable = createTableForPackageEntries(myImportLayoutList);
    return ScrollPaneFactory.createScrollPane(myImportLayoutTable);
  }

  public void reset() {
    myCbUseFQClassNames.setSelected(mySettings.USE_FQ_CLASS_NAMES);
    myCbUseFQClassNamesInJavaDoc.setSelected(mySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC);
    myCbUseSingleClassImports.setSelected(mySettings.USE_SINGLE_CLASS_IMPORTS);
    myCbInsertInnerClassImports.setSelected(mySettings.INSERT_INNER_CLASS_IMPORTS);
    myClassCountField.setText(Integer.toString(mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND));
    myNamesCountField.setText(Integer.toString(mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND));

    myImportLayoutList.copyFrom(mySettings.IMPORT_LAYOUT_TABLE);
    myPackageList.copyFrom(mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    
    myCbLayoutStaticImportsSeparately.setSelected(mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableDataChanged();

    model = (AbstractTableModel)myPackageTable.getModel();
    model.fireTableDataChanged();

    if(myImportLayoutTable.getRowCount() > 0) {
      myImportLayoutTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    if(myPackageTable.getRowCount() > 0) {
      myPackageTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    if (mySettings.JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST) {
      myJspImportCommaSeparated.doClick();
    }
    else {
      myJspOneImportPerDirective.doClick();
    }
    updateButtons();
  }

  public void apply() {
    stopTableEditing();

    mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = areStaticImportsEnabled();
    mySettings.USE_FQ_CLASS_NAMES = myCbUseFQClassNames.isSelected();
    mySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC = myCbUseFQClassNamesInJavaDoc.isSelected();
    mySettings.USE_SINGLE_CLASS_IMPORTS = myCbUseSingleClassImports.isSelected();
    mySettings.INSERT_INNER_CLASS_IMPORTS = myCbInsertInnerClassImports.isSelected();
    try{
      mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.parseInt(myClassCountField.getText());
    }
    catch(NumberFormatException e){
      //just a bad number
    }
    try{
      mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.parseInt(myNamesCountField.getText());
    }
    catch(NumberFormatException e){
      //just a bad number
    }

    myImportLayoutList.removeEmptyPackages();
    mySettings.IMPORT_LAYOUT_TABLE.copyFrom(myImportLayoutList);

    myPackageList.removeEmptyPackages();
    mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(myPackageList);

    mySettings.JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST = myJspImportCommaSeparated.isSelected();
  }


  private void stopTableEditing() {
    TableUtil.stopEditing(myImportLayoutTable);
    TableUtil.stopEditing(myPackageTable);
  }

  public boolean isModified() {
    boolean
    isModified  = isModified(myCbLayoutStaticImportsSeparately, mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY);
    isModified |= isModified(myCbUseFQClassNames, mySettings.USE_FQ_CLASS_NAMES);
    isModified |= isModified(myCbUseFQClassNamesInJavaDoc, mySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC);
    isModified |= isModified(myCbUseSingleClassImports, mySettings.USE_SINGLE_CLASS_IMPORTS);
    isModified |= isModified(myCbInsertInnerClassImports, mySettings.INSERT_INNER_CLASS_IMPORTS);
    isModified |= isModified(myClassCountField, mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND);
    isModified |= isModified(myNamesCountField, mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND);

    isModified |= isModified(myImportLayoutList, mySettings.IMPORT_LAYOUT_TABLE);
    isModified |= isModified(myPackageList, mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    isModified |= mySettings.JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST != myJspImportCommaSeparated.isSelected();

    return isModified;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(PackageEntryTable list, PackageEntryTable table) {
    if(list.getEntryCount() != table.getEntryCount()) {
      return true;
    }

    for(int i=0; i<list.getEntryCount(); i++) {
      PackageEntry entry1 = list.getEntryAt(i);
      PackageEntry entry2 = table.getEntryAt(i);
      if(!entry1.equals(entry2)) {
        return true;
      }
    }

    return false;
  }
}
