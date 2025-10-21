// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts;
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
import com.intellij.ui.components.JBBox;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EncapsulateFieldsDialog extends RefactoringDialog implements EncapsulateFieldsDescriptor {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsDialog.class);

  private static final int CHECKED_COLUMN = 0;
  private static final int FIELD_COLUMN = 1;
  private static final int GETTER_COLUMN = 2;
  private static final int SETTER_COLUMN = 3;

  private final EncapsulateFieldHelper myHelper;

  private final PsiClass myClass;

  private final PsiField[] myFields;
  private final ConcurrentHashMap<PsiField, Icon> myIconFields;
  private final boolean[] myCheckedMarks;
  private final boolean[] myFinalMarks;
  private final String[] myFieldNames;
  private final String[] myGetterNames;
  private final ConcurrentHashMap<Integer, PsiMethod> myGetterPrototypes;
  private final ConcurrentHashMap<Integer, RowIcon> myGetterPrototypesIcons;
  private final String[] mySetterNames;
  private final ConcurrentHashMap<Integer, PsiMethod> mySetterPrototypes;
  private final ConcurrentHashMap<Integer, RowIcon> mySetterPrototypesIcons;

  private JBTable myTable;
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
    this(project, EncapsulateFieldsContainer.create(aClass, preselectedFields, helper));
  }

  public EncapsulateFieldsDialog(Project project, EncapsulateFieldsContainer container) {
    super(project, true);
    myClass = container.myClass;
    myHelper = container.myHelper;
    myFields = container.myFields;
    myIconFields = new ConcurrentHashMap<>(container.myIconFields);
    myCheckedMarks = container.myCheckedMarks;
    myFinalMarks = container.myFinalMarks;
    myFieldNames = container.myFieldNames;
    myGetterNames = container.myGetterNames;
    myGetterPrototypes = new ConcurrentHashMap<>();
    for (int i = 0; i < container.myGetterPrototypes.length; i++) {
      PsiMethod prototype = container.myGetterPrototypes[i];
      if (prototype != null) {
        myGetterPrototypes.put(i, prototype);
      }
    }
    myGetterPrototypesIcons = new ConcurrentHashMap<>(container.myGetterPrototypesIcons);
    mySetterNames = container.mySetterNames;
    mySetterPrototypes = new ConcurrentHashMap<>();
    for (int i = 0; i < container.mySetterPrototypes.length; i++) {
      PsiMethod prototype = container.mySetterPrototypes[i];
      if (prototype != null) {
        mySetterPrototypes.put(i, prototype);
      }
    }
    mySetterPrototypesIcons = new ConcurrentHashMap<>(container.mySetterPrototypesIcons);

    String title = getRefactoringName();
    String qName = myClass.getQualifiedName();
    if (qName != null) {
      title += " - " + qName;
    }
    setTitle(title);
    init();
  }

  @Override
  public FieldDescriptor[] getSelectedFields() {
    int[] rows = getCheckedRows();
    FieldDescriptor[] descriptors = new FieldDescriptor[rows.length];

    for (int idx = 0; idx < rows.length; idx++) {
      String getterName = myGetterNames[rows[idx]];
      PsiField field = myFields[rows[idx]];
      String setterName = mySetterNames[rows[idx]];
      descriptors[idx] = new FieldDescriptorImpl(
        field,
        getterName,
        setterName,
        isToEncapsulateGet()
        ? myHelper.generateMethodPrototype(field, getterName, true)
        : null,
        isToEncapsulateSet()
        ? myHelper.generateMethodPrototype(field, setterName, false)
        : null
      );
    }
    return descriptors;
  }

  @Override
  public boolean isToEncapsulateGet() {
    return myCbEncapsulateGet.isSelected();
  }

  @Override
  public boolean isToEncapsulateSet() {
    return myCbEncapsulateSet.isSelected();
  }

  @Override
  public boolean isToUseAccessorsWhenAccessible() {
    if (getFieldsVisibility() == null) {
      // "as is"
      return true;
    }
    return myCbUseAccessorsWhenAccessible.isSelected();
  }

  @Override
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

  @Override
  public int getJavadocPolicy() {
    return myJavadocPolicy.getPolicy();
  }

  @Override
  public PsiClass getTargetClass() {
    return myClass;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.encapsulateFields.EncalpsulateFieldsDialog";
  }

  @Override
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

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createTable(), BorderLayout.CENTER);

    myCbEncapsulateGet.setText(JavaRefactoringBundle.message("encapsulate.fields.get.access.checkbox"));
    myCbEncapsulateSet.setText(JavaRefactoringBundle.message("encapsulate.fields.set.access.checkbox"));
    myCbUseAccessorsWhenAccessible.setText(JavaRefactoringBundle.message("encapsulate.fields.use.accessors.even.when.field.is.accessible.checkbox"));
    myRbFieldPrivate.setText(JavaRefactoringBundle.message("encapsulate.fields.private.radio"));
    myRbFieldProtected.setText(JavaRefactoringBundle.message("encapsulate.fields.protected.radio"));
    myRbFieldPackageLocal.setText(JavaRefactoringBundle.message("encapsulate.fields..package.local.radio"));
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
      @Override
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
      @Override
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

    JBBox leftBox = JBBox.createVerticalBox();
    myCbEncapsulateGet.setPreferredSize(myCbUseAccessorsWhenAccessible.getPreferredSize());
    leftBox.add(myCbEncapsulateGet);
    leftBox.add(myCbEncapsulateSet);
    leftBox.add(Box.createVerticalStrut(10));
    leftBox.add(myCbUseAccessorsWhenAccessible);
    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.setBorder(IdeBorderFactory.createTitledBorder(
      JavaRefactoringBundle.message("encapsulate.fields.encapsulate.border.title")));
    leftPanel.add(leftBox, BorderLayout.CENTER);
    leftPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    JPanel encapsulateBox = new JPanel(new BorderLayout());
    encapsulateBox.add(leftPanel, BorderLayout.CENTER);
    myJavadocPolicy = new DocCommentPanel(JavaBundle.message("encapsulate.fields.dialog.javadoc.title"));
    encapsulateBox.add(myJavadocPolicy, BorderLayout.EAST);
    boolean hasJavadoc = false;
    for (PsiField field : myFields) {
      if (field.getDocComment() != null) {
        hasJavadoc = true;
        break;
      }
    }
    myJavadocPolicy.setVisible(hasJavadoc);

    JBBox fieldsBox = JBBox.createVerticalBox();
    fieldsBox.add(myRbFieldPrivate);
    fieldsBox.add(myRbFieldPackageLocal);
    fieldsBox.add(myRbFieldProtected);
    fieldsBox.add(myRbFieldAsIs);
    JPanel fieldsVisibilityPanel = new JPanel(new BorderLayout());
    fieldsVisibilityPanel.setBorder(IdeBorderFactory.createTitledBorder(
      JavaRefactoringBundle.message("encapsulate.fields..encapsulated.fields.visibility.border.title")));
    fieldsVisibilityPanel.add(fieldsBox, BorderLayout.CENTER);
    fieldsVisibilityPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    JBBox methodsBox = JBBox.createVerticalBox();
    methodsBox.add(myRbAccessorPublic);
    methodsBox.add(myRbAccessorProtected);
    methodsBox.add(myRbAccessorPackageLocal);
    methodsBox.add(myRbAccessorPrivate);
    JPanel methodsVisibilityPanel = new JPanel(new BorderLayout());
    methodsVisibilityPanel.setBorder(IdeBorderFactory.createTitledBorder(
      JavaRefactoringBundle.message("encapsulate.fields.accessors.visibility.border.title")));
    methodsVisibilityPanel.add(methodsBox, BorderLayout.CENTER);
    methodsVisibilityPanel.add(Box.createHorizontalStrut(5), BorderLayout.WEST);

    JBBox visibilityBox = JBBox.createHorizontalBox();
    visibilityBox.add(fieldsVisibilityPanel);
    visibilityBox.add(Box.createHorizontalStrut(5));
    visibilityBox.add(methodsVisibilityPanel);

    JBBox box = JBBox.createVerticalBox();
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

    myTable.setPreferredScrollableViewportSize(JBUI.size(550, -1));
    myTable.setVisibleRowCount(12);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
//    JLabel label = new JLabel("Fields to Encapsulate");
//    CompTitledBorder titledBorder = new CompTitledBorder(label);
    JPanel panel = new JPanel(new BorderLayout());
    Border border = IdeBorderFactory.createTitledBorder(
      JavaRefactoringBundle.message("encapsulate.fields.fields.to.encapsulate.border.title"), false);
    panel.setBorder(border);
    panel.add(scrollPane);

    // make ESC and ENTER work when focus is in the table
    myTable.registerKeyboardAction(new ActionListener() {
      @Override
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
      @Override
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
      @Override
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @Override
  protected void doAction() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    String errorString = validateData();
    if (errorString != null) { // were errors
      CommonRefactoringUtil.showErrorMessage(getRefactoringName(), errorString, HelpID.ENCAPSULATE_FIELDS, myProject);
      return;
    }

    if (getCheckedRows().length == 0) {
      String noTargetMessage = JavaRefactoringBundle.message("encapsulate.fields.no.target");
      CommonRefactoringUtil.showErrorMessage(getRefactoringName(), noTargetMessage, HelpID.ENCAPSULATE_FIELDS, myProject);
      return;
    }

    EncapsulateFieldsProcessor processor = ActionUtil.underModalProgress(myProject,
                                                                         CodeInsightBundle.message("progress.title.resolving.reference"),
                                                                         () -> new EncapsulateFieldsProcessor(myProject, this));
    invokeRefactoring(processor);
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE = myCbUseAccessorsWhenAccessible.isSelected();
  }

  /**
   * @return error string if errors were found, or null if everything is ok
   */
  private @NlsContexts.DialogMessage String validateData() {
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

  @Override
  protected String getHelpId() {
    return HelpID.ENCAPSULATE_FIELDS;
  }

  private class MyTableModel extends AbstractTableModel {
    @Override
    public int getColumnCount() {
      return 4;
    }

    @Override
    public int getRowCount() {
      return myFields.length;
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return switch (columnIndex) {
        case CHECKED_COLUMN -> myCheckedMarks[rowIndex];
        case FIELD_COLUMN -> myFieldNames[rowIndex];
        case GETTER_COLUMN -> myGetterNames[rowIndex];
        case SETTER_COLUMN -> mySetterNames[rowIndex];
        default -> throw new RuntimeException("Incorrect column index");
      };
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case CHECKED_COLUMN -> " ";
        case FIELD_COLUMN -> JavaRefactoringBundle.message("encapsulate.fields.field.column.name");
        case GETTER_COLUMN -> JavaRefactoringBundle.message("encapsulate.fields.getter.column.name");
        case SETTER_COLUMN -> JavaRefactoringBundle.message("encapsulate.fields.setter.column.name");
        default -> throw new RuntimeException("Incorrect column index");
      };
    }

    @Override
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

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        myCheckedMarks[rowIndex] = ((Boolean)aValue).booleanValue();
        fireTableRowsUpdated(rowIndex, rowIndex);
      }
      else {
        String name = (String)aValue;
        PsiField field = myFields[rowIndex];
        switch (columnIndex) {
          case GETTER_COLUMN -> myGetterNames[rowIndex] = name;
          case SETTER_COLUMN -> mySetterNames[rowIndex] = name;
          default -> throw new RuntimeException("Incorrect column index");
        }
        ReadAction.nonBlocking(() -> {
            switch (columnIndex) {
              case GETTER_COLUMN -> {
                PsiMethod method = myHelper.generateMethodPrototype(field, name, true);
                if (method != null) {
                  myGetterPrototypes.put(rowIndex, method);
                }
                myGetterPrototypesIcons.put(rowIndex, EncapsulateFieldsContainer.getIcon(myGetterPrototypes.get(rowIndex), myClass));
              }
              case SETTER_COLUMN -> {
                PsiMethod method = myHelper.generateMethodPrototype(field, name, false);
                if (method != null) {
                  mySetterPrototypes.put(rowIndex, method);
                }
                mySetterPrototypesIcons.put(rowIndex, EncapsulateFieldsContainer.getIcon(mySetterPrototypes.get(rowIndex), myClass));
              }
              default -> throw new RuntimeException("Incorrect column index");
            }
            return null;
          })
          .finishOnUiThread(ModalityState.stateForComponent(EncapsulateFieldsDialog.this.myTable), nill->{
            EncapsulateFieldsDialog.this.repaint();
          })
          .expireWith(EncapsulateFieldsDialog.this.getDisposable())
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    }
  }

  private class MyTableRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, final Object value,
                                                   boolean isSelected, boolean hasFocus, final int row,
                                                   final int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final int modelColumn = myTable.convertColumnIndexToModel(column);

      this.setIconTextGap(0);
      PsiField field = myFields[row];
      switch (modelColumn) {
        case FIELD_COLUMN -> {
          Icon icon = myIconFields.get(field);
          if (icon != null) {
            setIcon(icon);
            setDisabledIcon(icon);
          }
          configureColors(isSelected, table, hasFocus, row, column);
        }
        case GETTER_COLUMN, SETTER_COLUMN -> {

          PsiMethod prototype = modelColumn == GETTER_COLUMN ? myGetterPrototypes.get(row) : mySetterPrototypes.get(row);
          RowIcon rowIcon = modelColumn == GETTER_COLUMN ? myGetterPrototypesIcons.get(row) : mySetterPrototypesIcons.get(row);
          if (prototype != null) {
            //              MyTableRenderer.this.setForeground(Color.black);
            configureColors(isSelected, table, hasFocus, row, column);
          }
          else {
            setForeground(JBColor.RED);
          }

          if (rowIcon != null) {
            setIcon(rowIcon);
            setDisabledIcon(rowIcon);
          }
        }
        default -> {
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
      setForeground(isSelected ? UIUtil.getTableSelectionForeground(true) : UIUtil.getTableForeground());
      setBackground(isSelected ? UIUtil.getTableSelectionBackground(true) : UIUtil.getTableBackground());
      if (hasFocus) {
        if (table.isCellEditable(row, column)) {
          super.setForeground(UIUtil.getTableFocusCellForeground());
          super.setBackground(UIUtil.getTableFocusCellBackground());
        }
      }
    }
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("encapsulate.fields.title");
  }

  public record EncapsulateFieldsContainer(
    EncapsulateFieldHelper myHelper,
    PsiClass myClass,
    PsiField[] myFields,
    Map<PsiField, Icon> myIconFields,
    boolean[] myCheckedMarks,
    boolean[] myFinalMarks,
    String[] myFieldNames,
    String[] myGetterNames,
    PsiMethod[] myGetterPrototypes,
    Map<Integer, RowIcon> myGetterPrototypesIcons,
    String[] mySetterNames,
    PsiMethod[] mySetterPrototypes,
    Map<Integer, RowIcon> mySetterPrototypesIcons
  ) {
    @SuppressWarnings("UnnecessaryLocalVariable")
    static EncapsulateFieldsContainer create(@NotNull PsiClass aClass,
                                             @NotNull Set preselectedFields,
                                             @NotNull EncapsulateFieldHelper helper) {
      PsiClass myClass = aClass;
      EncapsulateFieldHelper myHelper = helper;
      PsiField[] myFields = helper.getApplicableFields(myClass);
      Map<PsiField, Icon> myIconFields = new HashMap<>();
      for (PsiField field : myFields) {
        myIconFields.put(field, field.getIcon(Iconable.ICON_FLAG_VISIBILITY));
      }
      String[] myFieldNames = new String[myFields.length];
      boolean[] myCheckedMarks = new boolean[myFields.length];
      boolean[] myFinalMarks = new boolean[myFields.length];
      String[] myGetterNames = new String[myFields.length];
      String[] mySetterNames = new String[myFields.length];
      PsiMethod[] myGetterPrototypes = new PsiMethod[myFields.length];
      PsiMethod[] mySetterPrototypes = new PsiMethod[myFields.length];
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
      HashMap<Integer, RowIcon> myGetterPrototypesIcons = new HashMap<>();
      HashMap<Integer, RowIcon> mySetterPrototypesIcons = new HashMap<>();
      for (int i = 0; i < myGetterPrototypes.length; i++) {
        PsiMethod prototype = myGetterPrototypes[i];
        myGetterPrototypesIcons.put(i, getIcon(prototype, myClass));
      }
      for (int i = 0; i < mySetterPrototypes.length; i++) {
        PsiMethod prototype = mySetterPrototypes[i];
        mySetterPrototypesIcons.put(i, getIcon(prototype, myClass));
      }
      return new EncapsulateFieldsContainer(myHelper, myClass, myFields, myIconFields, myCheckedMarks, myFinalMarks, myFieldNames,
                                            myGetterNames, myGetterPrototypes, myGetterPrototypesIcons,
                                            mySetterNames, mySetterPrototypes, mySetterPrototypesIcons);
    }

    private static @NotNull RowIcon getIcon(@Nullable PsiMethod prototype, @Nullable PsiClass psiClass) {
      Icon methodIcon = IconUtil.getEmptyIcon(true);
      Icon overrideIcon = EmptyIcon.ICON_16;

      if (prototype != null && psiClass != null) {

        PsiMethod existing = psiClass.findMethodBySignature(prototype, false);
        if (existing != null) {
          methodIcon = existing.getIcon(Iconable.ICON_FLAG_VISIBILITY);
        }

        PsiMethod[] superMethods = prototype.findSuperMethods(psiClass);
        if (superMethods.length > 0) {
          if (!superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT)) {
            overrideIcon = AllIcons.General.OverridingMethod;
          }
          else {
            overrideIcon = AllIcons.General.ImplementingMethod;
          }
        }
      }
      return IconManager.getInstance().createRowIcon(methodIcon, overrideIcon);
    }
  }
}
