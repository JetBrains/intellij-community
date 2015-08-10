/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.encapsulateFields;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

public class EncapsulateFieldsDialog extends RefactoringDialog implements EncapsulateFieldsDescriptor {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsDialog.class);

  private static final String REFACTORING_NAME = RefactoringBundle.message("encapsulate.fields.title");

  private static final int CHECKED_COLUMN = 0;
  private static final int FIELD_COLUMN = 1;
  private static final int GETTER_COLUMN = 2;
  private static final int SETTER_COLUMN = 3;

  private final EncapsulateFieldHelper myHelper;

  private final Project myProject;
  private final PsiClass myClass;

  private final PsiField[] myFields;
  private final boolean[] myCheckedMarks;
  private final boolean[] myFinalMarks;
  private final String[] myFieldNames;
  private final String[] myGetterNames;
  private final PsiMethod[] myGetterPrototypes;
  private final String[] mySetterNames;
  private final PsiMethod[] mySetterPrototypes;

  private JTable myTable;
  private MyTableModel myTableModel;

  private final JCheckBox myCbEncapsulateGet = new NonFocusableCheckBox();
  private final JCheckBox myCbEncapsulateSet = new NonFocusableCheckBox();
  private final JCheckBox myCbUseAccessorsWhenAccessible = new NonFocusableCheckBox();
  private final JRadioButton myRbFieldPrivate = new JRadioButton();
  private final JRadioButton myRbFieldProtected = new JRadioButton();
  private final JRadioButton myRbFieldPackageLocal = new JRadioButton();
  private final JRadioButton myRbFieldAsIs = new JRadioButton();
  private final JRadioButton myRbAccessorPublic = new JRadioButton();
  private final JRadioButton myRbAccessorProtected = new JRadioButton();
  private final JRadioButton myRbAccessorPrivate = new JRadioButton();
  private final JRadioButton myRbAccessorPackageLocal = new JRadioButton();
  private DocCommentPanel myJavadocPolicy;

  private boolean myCbUseAccessorWhenAccessibleValue;

  {
    myRbAccessorPackageLocal.setFocusable(false);
    myRbAccessorPrivate.setFocusable(false);
    myRbAccessorProtected.setFocusable(false);
    myRbAccessorPublic.setFocusable(false);

    myRbFieldAsIs.setFocusable(false);
    myRbFieldPackageLocal.setFocusable(false);
    myRbFieldPrivate.setFocusable(false);
    myRbFieldProtected.setFocusable(false);
  }

  public EncapsulateFieldsDialog(Project project, PsiClass aClass, final Set preselectedFields, EncapsulateFieldHelper helper) {
    super(project, true);
    myProject = project;
    myClass = aClass;
    myHelper = helper;

    String title = REFACTORING_NAME;
    String qName = myClass.getQualifiedName();
    if (qName != null) {
      title += " - " + qName;
    }
    setTitle(title);

    myFields = myHelper.getApplicableFields(myClass);
    myFieldNames = new String[myFields.length];
    myCheckedMarks = new boolean[myFields.length];
    myFinalMarks = new boolean[myFields.length];
    myGetterNames = new String[myFields.length];
    mySetterNames = new String[myFields.length];
    myGetterPrototypes = new PsiMethod[myFields.length];
    mySetterPrototypes = new PsiMethod[myFields.length];
    for (int idx = 0; idx < myFields.length; idx++) {
      PsiField field = myFields[idx];
      myCheckedMarks[idx] = preselectedFields.contains(field);
      myFinalMarks[idx] = field.hasModifierProperty(PsiModifier.FINAL);
      myFieldNames[idx] =
              PsiFormatUtil.formatVariable(field,
                                           PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER,
                                           PsiSubstitutor.EMPTY
              );
      myGetterNames[idx] = myHelper.suggestGetterName(field);
      mySetterNames[idx] = myHelper.suggestSetterName(field);
      myGetterPrototypes[idx] = myHelper.generateMethodPrototype(field, myGetterNames[idx], true);
      mySetterPrototypes[idx] = myHelper.generateMethodPrototype(field, mySetterNames[idx], false);
    }

    init();
  }

  public FieldDescriptor[] getSelectedFields() {
    int[] rows = getCheckedRows();
    FieldDescriptor[] descriptors = new FieldDescriptor[rows.length];

    for (int idx = 0; idx < rows.length; idx++) {
      descriptors[idx] = new FieldDescriptorImpl(
        myFields[rows[idx]],
        myGetterNames[rows[idx]],
        mySetterNames[rows[idx]],
        isToEncapsulateGet()
          ? myGetterPrototypes[rows[idx]]
          : null,
        isToEncapsulateSet()
          ? mySetterPrototypes[rows[idx]]
          : null
      );
    }
    return descriptors;
  }

  public boolean isToEncapsulateGet() {
    return myCbEncapsulateGet.isSelected();
  }

  public boolean isToEncapsulateSet() {
    return myCbEncapsulateSet.isSelected();
  }

  public boolean isToUseAccessorsWhenAccessible() {
    if (getFieldsVisibility() == null) {
      // "as is"
      return true;
    }
    return myCbUseAccessorsWhenAccessible.isSelected();
  }

  @PsiModifier.ModifierConstant
  public String getFieldsVisibility() {
    if (myRbFieldPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    } else if (myRbFieldPackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    } else if (myRbFieldProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    } else if (myRbFieldAsIs.isSelected()) {
      return null;
    } else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public int getJavadocPolicy() {
    return myJavadocPolicy.getPolicy();
  }

  @Override
  public PsiClass getTargetClass() {
    return myClass;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.encapsulateFields.EncalpsulateFieldsDialog";
  }

  @PsiModifier.ModifierConstant
  public String getAccessorsVisibility() {
    if (myRbAccessorPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    } else if (myRbAccessorProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    } else if (myRbAccessorPackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    } else if (myRbAccessorPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    } else {
      LOG.assertTrue(false);
      return null;
    }
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createTable(), BorderLayout.CENTER);

    myCbEncapsulateGet.setText(RefactoringBundle.message("encapsulate.fields.get.access.checkbox"));
    myCbEncapsulateSet.setText(RefactoringBundle.message("encapsulate.fields.set.access.checkbox"));
    myCbUseAccessorsWhenAccessible.setText(RefactoringBundle.message("encapsulate.fields.use.accessors.even.when.field.is.accessible.checkbox"));
    myRbFieldPrivate.setText(RefactoringBundle.message("encapsulate.fields.private.radio"));
    myRbFieldProtected.setText(RefactoringBundle.message("encapsulate.fields.protected.radio"));
    myRbFieldPackageLocal.setText(RefactoringBundle.message("encapsulate.fields..package.local.radio"));
    myRbFieldAsIs.setText(RefactoringBundle.getVisibilityAsIs());
    myRbAccessorPublic.setText(RefactoringBundle.getVisibilityPublic());
    myRbAccessorProtected.setText(RefactoringBundle.getVisibilityProtected());
    myRbAccessorPrivate.setText(RefactoringBundle.getVisibilityPrivate());
    myRbAccessorPackageLocal.setText(RefactoringBundle.getVisibilityPackageLocal());

    ButtonGroup fieldGroup = new ButtonGroup();
    fieldGroup.add(myRbFieldAsIs);
    fieldGroup.add(myRbFieldPackageLocal);
    fieldGroup.add(myRbFieldPrivate);
    fieldGroup.add(myRbFieldProtected);

    ButtonGroup methodGroup = new ButtonGroup();
    methodGroup.add(myRbAccessorPackageLocal);
    methodGroup.add(myRbAccessorPrivate);
    methodGroup.add(myRbAccessorProtected);
    methodGroup.add(myRbAccessorPublic);

    myCbEncapsulateGet.setSelected(true);
    myCbEncapsulateSet.setSelected(true);
    ActionListener checkboxListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myCbEncapsulateGet.equals(e.getSource())) {
          if (!myCbEncapsulateGet.isSelected()) {
            myCbEncapsulateSet.setSelected(true);
          }
        } else {
          // myCbEncapsulateSet is the source
          if (!myCbEncapsulateSet.isSelected()) {
            myCbEncapsulateGet.setSelected(true);
          }
        }
        int[] rows = myTable.getSelectedRows();
        myTableModel.fireTableDataChanged();
        TableUtil.selectRows(myTable, rows);
      }
    };
    myCbEncapsulateGet.addActionListener(checkboxListener);
    myCbEncapsulateSet.addActionListener(checkboxListener);
    myRbFieldAsIs.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (myRbFieldAsIs.isSelected()) {
          myCbUseAccessorWhenAccessibleValue = myCbUseAccessorsWhenAccessible.isSelected();

          myCbUseAccessorsWhenAccessible.setSelected(true);
          myCbUseAccessorsWhenAccessible.setEnabled(false);
        }
        else {
          myCbUseAccessorsWhenAccessible.setEnabled(true);
          myCbUseAccessorsWhenAccessible.setSelected(myCbUseAccessorWhenAccessibleValue);
        }
      }
    }
    );
    myCbUseAccessorsWhenAccessible.setSelected(
            JavaRefactoringSettings.getInstance().ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE
    );
    myCbUseAccessorWhenAccessibleValue = myCbUseAccessorsWhenAccessible.isSelected();

    myRbFieldPrivate.setSelected(true);
    myRbAccessorPublic.setSelected(true);

    Box leftBox = Box.createVerticalBox();
    myCbEncapsulateGet.setPreferredSize(myCbUseAccessorsWhenAccessible.getPreferredSize());
    leftBox.add(myCbEncapsulateGet);
    leftBox.add(myCbEncapsulateSet);
    leftBox.add(Box.createVerticalStrut(10));
    leftBox.add(myCbUseAccessorsWhenAccessible);
    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.setBorder(IdeBorderFactory.createTitledBorder(
      RefactoringBundle.message("encapsulate.fields.encapsulate.border.title"), true));
    leftPanel.add(leftBox, BorderLayout.CENTER);
    leftPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    JPanel encapsulateBox = new JPanel(new BorderLayout());
    encapsulateBox.add(leftPanel, BorderLayout.CENTER);
    myJavadocPolicy = new DocCommentPanel("JavaDoc");
    encapsulateBox.add(myJavadocPolicy, BorderLayout.EAST);
    boolean hasJavadoc = false;
    for (PsiField field : myFields) {
      if (field.getDocComment() != null) {
        hasJavadoc = true;
        break;
      }
    }
    myJavadocPolicy.setVisible(hasJavadoc);

    Box fieldsBox = Box.createVerticalBox();
    fieldsBox.add(myRbFieldPrivate);
    fieldsBox.add(myRbFieldPackageLocal);
    fieldsBox.add(myRbFieldProtected);
    fieldsBox.add(myRbFieldAsIs);
    JPanel fieldsVisibilityPanel = new JPanel(new BorderLayout());
    fieldsVisibilityPanel.setBorder(IdeBorderFactory.createTitledBorder(
      RefactoringBundle.message("encapsulate.fields..encapsulated.fields.visibility.border.title"), true));
    fieldsVisibilityPanel.add(fieldsBox, BorderLayout.CENTER);
    fieldsVisibilityPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    Box methodsBox = Box.createVerticalBox();
    methodsBox.add(myRbAccessorPublic);
    methodsBox.add(myRbAccessorProtected);
    methodsBox.add(myRbAccessorPackageLocal);
    methodsBox.add(myRbAccessorPrivate);
    JPanel methodsVisibilityPanel = new JPanel(new BorderLayout());
    methodsVisibilityPanel.setBorder(IdeBorderFactory.createTitledBorder(
      RefactoringBundle.message("encapsulate.fields.accessors.visibility.border.title"), true));
    methodsVisibilityPanel.add(methodsBox, BorderLayout.CENTER);
    methodsVisibilityPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    Box visibilityBox = Box.createHorizontalBox();
    visibilityBox.add(fieldsVisibilityPanel);
    visibilityBox.add(Box.createHorizontalStrut(5));
    visibilityBox.add(methodsVisibilityPanel);

    Box box = Box.createVerticalBox();
    box.add(encapsulateBox);
    box.add(Box.createVerticalStrut(5));
    box.add(visibilityBox);

    JPanel boxPanel = new JPanel(new BorderLayout());
    boxPanel.add(box, BorderLayout.CENTER);
    boxPanel.add(Box.createVerticalStrut(5), BorderLayout.NORTH);
    panel.add(boxPanel, BorderLayout.SOUTH);

    return panel;
  }

  private JComponent createTable() {
    myTableModel = new MyTableModel();
    myTable = new JBTable(myTableModel);
    myTable.setSurrendersFocusOnKeystroke(true);
    MyTableRenderer renderer = new MyTableRenderer();
    TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(CHECKED_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
    columnModel.getColumn(FIELD_COLUMN).setCellRenderer(renderer);
    columnModel.getColumn(GETTER_COLUMN).setCellRenderer(renderer);
    columnModel.getColumn(SETTER_COLUMN).setCellRenderer(renderer);
    TableUtil.setupCheckboxColumn(columnModel.getColumn(CHECKED_COLUMN));

    myTable.setPreferredScrollableViewportSize(new Dimension(550, myTable.getRowHeight() * 12));
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
//    JLabel label = new JLabel("Fields to Encapsulate");
//    CompTitledBorder titledBorder = new CompTitledBorder(label);
    JPanel panel = new JPanel(new BorderLayout());
    Border border = IdeBorderFactory.createTitledBorder(
      RefactoringBundle.message("encapsulate.fields.fields.to.encapsulate.border.title"), false);
    panel.setBorder(border);
    panel.add(scrollPane);

    // make ESC and ENTER work when focus is in the table
    myTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.cancelCellEditing();
        } else {
          doCancelAction();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );
    // make SPACE check/uncheck selected rows
    @NonNls InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          boolean valueToBeSet = false;
          for (int row : rows) {
            if (!myCheckedMarks[row]) {
              valueToBeSet = true;
              break;
            }
          }
          for (int row : rows) {
            myCheckedMarks[row] = valueToBeSet;
          }
          myTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
          TableUtil.selectRows(myTable, rows);
        }
      }
    }
    );
    // make ENTER work when the table has focus
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invokeImpl");
    actionMap.put("invokeImpl", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        } else {
          clickDefaultButton();
        }
      }
    }
    );
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected void doAction() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    String errorString = validateData();
    if (errorString != null) { // were errors
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, errorString, HelpID.ENCAPSULATE_FIELDS, myProject);
      return;
    }

    if (getCheckedRows().length == 0) {
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, "Nothing found to encapsulate", HelpID.ENCAPSULATE_FIELDS, myProject);
      return;
    }

    invokeRefactoring(new EncapsulateFieldsProcessor(myProject, this));
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE = myCbUseAccessorsWhenAccessible.isSelected();
  }

  /**
   * @return error string if errors were found, or null if everything is ok
   */
  private String validateData() {
    PsiManager manager = PsiManager.getInstance(myProject);
    for (int idx = 0; idx < myFields.length; idx++) {
      if (myCheckedMarks[idx]) {
        String name;
        if (isToEncapsulateGet()) {
          name = myGetterNames[idx];
          if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(name)) {
            return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
          }
        }
        if (!myFinalMarks[idx] && isToEncapsulateSet()) {
          name = mySetterNames[idx];
          if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(name)) {
            return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
          }
        }
      }
    }
    return null;
  }

  @Override
  protected boolean areButtonsValid() {
    return getCheckedRows().length > 0;
  }

  private int[] getCheckedRows() {
    int count = 0;
    for (boolean checkedMark : myCheckedMarks) {
      if (checkedMark) {
        count++;
      }
    }
    int[] rows = new int[count];
    int currentRow = 0;
    for (int idx = 0; idx < myCheckedMarks.length; idx++) {
      if (myCheckedMarks[idx]) {
        rows[currentRow++] = idx;
      }
    }
    return rows;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.ENCAPSULATE_FIELDS);
  }

  private class MyTableModel extends AbstractTableModel {
    public int getColumnCount() {
      return 4;
    }

    public int getRowCount() {
      return myFields.length;
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKED_COLUMN:
          return myCheckedMarks[rowIndex];
        case FIELD_COLUMN:
          return myFieldNames[rowIndex];
        case GETTER_COLUMN:
          return myGetterNames[rowIndex];
        case SETTER_COLUMN:
          return mySetterNames[rowIndex];
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public String getColumnName(int column) {
      switch (column) {
        case CHECKED_COLUMN:
          return " ";
        case FIELD_COLUMN:
          return RefactoringBundle.message("encapsulate.fields.field.column.name");
        case GETTER_COLUMN:
          return RefactoringBundle.message("encapsulate.fields.getter.column.name");
        case SETTER_COLUMN:
          return RefactoringBundle.message("encapsulate.fields.setter.column.name");
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) return true;
      if (myCheckedMarks[rowIndex]) {
        if (columnIndex == GETTER_COLUMN && myCbEncapsulateGet.isSelected()) return true;
        if (columnIndex == SETTER_COLUMN) {
          if (!myFinalMarks[rowIndex] && myCbEncapsulateSet.isSelected()) return true;
        }
      }
      return false;
    }

    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        myCheckedMarks[rowIndex] = ((Boolean) aValue).booleanValue();
        fireTableRowsUpdated(rowIndex, rowIndex);
      } else {
        String name = (String) aValue;
        PsiField field = myFields[rowIndex];
        switch (columnIndex) {
          case GETTER_COLUMN:
            myGetterNames[rowIndex] = name;
            myGetterPrototypes[rowIndex] = myHelper.generateMethodPrototype(field, name, true);
            break;

          case SETTER_COLUMN:
            mySetterNames[rowIndex] = name;
            mySetterPrototypes[rowIndex] = myHelper.generateMethodPrototype(field, name, false);
            break;

          default:
            throw new RuntimeException("Incorrect column index");
        }
      }
    }
  }

  private class MyTableRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, final Object value,
                                                   boolean isSelected, boolean hasFocus, final int row,
                                                   final int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final int modelColumn = myTable.convertColumnIndexToModel(column);

      this.setIconTextGap(0);
      PsiField field = myFields[row];
      switch (modelColumn) {
        case FIELD_COLUMN:
          {
            Icon icon = field.getIcon(Iconable.ICON_FLAG_VISIBILITY);
            setIcon(icon);
            setDisabledIcon(icon);
            configureColors(isSelected, table, hasFocus, row, column);
            break;
          }

        case GETTER_COLUMN:
        case SETTER_COLUMN:
          {
            Icon methodIcon = IconUtil.getEmptyIcon(true);
            Icon overrideIcon = EmptyIcon.ICON_16;

            PsiMethod prototype = modelColumn == GETTER_COLUMN ? myGetterPrototypes[row] : mySetterPrototypes[row];
            if (prototype != null) {
//              MyTableRenderer.this.setForeground(Color.black);
              configureColors(isSelected, table, hasFocus, row, column);

              PsiMethod existing = myClass.findMethodBySignature(prototype, false);
              if (existing != null) {
                methodIcon = existing.getIcon(Iconable.ICON_FLAG_VISIBILITY);
              }

              PsiMethod[] superMethods = prototype.findSuperMethods(myClass);
              if (superMethods.length > 0) {
                if (!superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT)) {
                  overrideIcon = AllIcons.General.OverridingMethod;
                } else {
                  overrideIcon = AllIcons.General.ImplementingMethod;
                }
              }
            } else {
              setForeground(JBColor.RED);
            }

            RowIcon icon = new RowIcon(methodIcon, overrideIcon);
            setIcon(icon);
            setDisabledIcon(icon);
            break;
          }

        default:
          {
            setIcon(null);
            setDisabledIcon(null);
          }
      }
      boolean enabled = myCheckedMarks[row];
      if (enabled) {
        if (modelColumn == GETTER_COLUMN) {
          enabled = myCbEncapsulateGet.isSelected();
        } else if (modelColumn == SETTER_COLUMN) {
          enabled = !myFinalMarks[row] && myCbEncapsulateSet.isSelected();
        }
      }
      this.setEnabled(enabled);
      return this;
    }

    private void configureColors(boolean isSelected, JTable table, boolean hasFocus, final int row, final int column) {
      setForeground(isSelected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground());
      setBackground(isSelected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground());
      if (hasFocus) {
        if (table.isCellEditable(row, column)) {
          super.setForeground(UIUtil.getTableFocusCellForeground());
          super.setBackground(UIUtil.getTableFocusCellBackground());
        }
      }
    }
  }

}
