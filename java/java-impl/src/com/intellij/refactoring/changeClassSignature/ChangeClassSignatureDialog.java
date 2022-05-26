// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature;

import com.intellij.codeInsight.daemon.impl.quickfix.ChangeClassSignatureFromUsageFix;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dsl
 * @author Konstantin Bulenkov
 */
public class ChangeClassSignatureDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance(ChangeClassSignatureDialog.class);
  private static final int NAME_COLUMN = 0;
  private static final int BOUND_VALUE_COLUMN = 1;
  private static final int DEFAULT_VALUE_COLUMN = 2;

  private final List<TypeParameterInfo> myTypeParameterInfos;
  private final List<PsiTypeCodeFragment> myBoundValueTypeCodeFragments;
  private final List<PsiTypeCodeFragment> myDefaultValueTypeCodeFragments;
  private final PsiClass myClass;
  private final PsiTypeParameter[] myOriginalParameters;
  private final MyTableModel myTableModel;
  private JBTable myTable;
  private final boolean myHideDefaultValueColumn;

  public ChangeClassSignatureDialog(@NotNull PsiClass aClass, boolean hideDefaultValueColumn) {
    this(
      aClass,
      initTypeParameterInfos(aClass.getTypeParameters().length),
      hideDefaultValueColumn
    );
  }

  @NotNull
  private static List<ChangeClassSignatureFromUsageFix.TypeParameterInfoView> initTypeParameterInfos(int length) {
    final List<ChangeClassSignatureFromUsageFix.TypeParameterInfoView> result =
      new ArrayList<>();
    for (int i = 0; i < length; i++) {
      result.add(new ChangeClassSignatureFromUsageFix.TypeParameterInfoView(new TypeParameterInfo.Existing(i), null, null));
    }
    return result;
  }

  public ChangeClassSignatureDialog(@NotNull PsiClass aClass,
                                    @NotNull List<? extends ChangeClassSignatureFromUsageFix.TypeParameterInfoView> parameters,
                                    boolean hideDefaultValueColumn) {
    super(aClass.getProject(), true);
    myHideDefaultValueColumn = hideDefaultValueColumn;
    setTitle(getRefactoringName());
    myClass = aClass;
    myOriginalParameters = myClass.getTypeParameters();


    myTypeParameterInfos = new ArrayList<>(parameters.size());
    myBoundValueTypeCodeFragments = new ArrayList<>(parameters.size());
    myDefaultValueTypeCodeFragments = new ArrayList<>(parameters.size());
    for (ChangeClassSignatureFromUsageFix.TypeParameterInfoView p : parameters) {
      myTypeParameterInfos.add(p.getInfo());
      myBoundValueTypeCodeFragments.add(p.getBoundValueFragment());
      myDefaultValueTypeCodeFragments.add(p.getDefaultValueFragment());
    }

    myTableModel = new MyTableModel();
    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    return new JLabel(JavaRefactoringBundle.message("changeClassSignature.class.label.text", DescriptiveNameUtil.getDescriptiveName(myClass)));
  }

  @Override
  protected String getHelpId() {
    return HelpID.CHANGE_CLASS_SIGNATURE;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @Override
  protected JComponent createCenterPanel() {
    myTable = new JBTable(myTableModel);
    myTable.setShowGrid(false);
    TableColumn nameColumn = myTable.getColumnModel().getColumn(NAME_COLUMN);
    TableColumn boundColumn = myTable.getColumnModel().getColumn(BOUND_VALUE_COLUMN);
    TableColumn valueColumn = myTable.getColumnModel().getColumn(DEFAULT_VALUE_COLUMN);
    Project project = myClass.getProject();
    nameColumn.setCellRenderer(new MyCellRenderer());
    nameColumn.setCellEditor(new StringTableCellEditor(project));
    boundColumn.setCellRenderer(new CodeFragmentTableCellRenderer(project));
    boundColumn.setCellEditor(new JavaCodeFragmentTableCellEditor(project));
    valueColumn.setCellRenderer(new CodeFragmentTableCellRenderer(project));
    valueColumn.setCellEditor(new JavaCodeFragmentTableCellEditor(project));

    myTable.setPreferredScrollableViewportSize(JBUI.size(210, -1));
    myTable.setVisibleRowCount(4);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().setSelectionInterval(0, 0);
    myTable.setSurrendersFocusOnKeystroke(true);
    myTable.setCellSelectionEnabled(true);
    myTable.setFocusCycleRoot(true);

    if (myHideDefaultValueColumn) {
      final TableColumn defaultValue = myTable.getColumnModel().getColumn(DEFAULT_VALUE_COLUMN);
      myTable.removeColumn(defaultValue);
      myTable.getModel().addTableModelListener(new TableModelListener() {
        @Override
        public void tableChanged(TableModelEvent e) {
          if (e.getType() == TableModelEvent.INSERT) {
            myTable.getModel().removeTableModelListener(this);
            final TableColumnAnimator animator = new TableColumnAnimator(myTable);
            animator.setStep(20);
            animator.addColumn(defaultValue, myTable.getWidth() / 2);
            animator.startAndDoWhenDone(() -> myTable.editCellAt(myTable.getRowCount() - 1, 0));
            animator.start();
          }
        }
      });
    }

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(JavaRefactoringBundle.message("changeClassSignature.parameters.panel.border.title"), myTable), BorderLayout.NORTH);
    panel.add(ToolbarDecorator.createDecorator(myTable).createPanel(), BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void doAction() {
    TableUtil.stopEditing(myTable);
    String message = validateAndCommitData();
    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(JavaRefactoringBundle.message("error.incorrect.data"), message, HelpID.CHANGE_CLASS_SIGNATURE, myClass.getProject());
      return;
    }
    var provider = JavaSpecialRefactoringProvider.getInstance();
    var processor = provider.getChangeClassSignatureProcessor(myClass.getProject(), myClass,
                                                              myTypeParameterInfos.toArray(new TypeParameterInfo[0]));
    invokeRefactoring(processor);
  }

  @NlsContexts.DialogMessage
  private String validateAndCommitData() {
    final PsiTypeParameter[] parameters = myClass.getTypeParameters();
    final Map<String, TypeParameterInfo> infos = new HashMap<>();
    for (final TypeParameterInfo info : myTypeParameterInfos) {
      if (info instanceof TypeParameterInfo.New &&
          !PsiNameHelper.getInstance(myClass.getProject()).isIdentifier(info.getName(parameters))) {
        return JavaRefactoringBundle.message("error.wrong.name.input", info.getName(parameters));
      }
      final String newName = info.getName(parameters);
      TypeParameterInfo existing = infos.get(newName);
      if (existing != null) {
        return JavaRefactoringBundle.message("changeClassSignature.already.contains.type.parameter", myClass.getName(), newName);
      }
      infos.put(newName, info);
    }
    LOG.assertTrue(myDefaultValueTypeCodeFragments.size() == myTypeParameterInfos.size());
    LOG.assertTrue(myBoundValueTypeCodeFragments.size() == myTypeParameterInfos.size());
    for (int i = 0; i < myDefaultValueTypeCodeFragments.size(); i++) {
      TypeParameterInfo info = myTypeParameterInfos.get(i);
      if (info instanceof TypeParameterInfo.Existing) continue;
      String message = updateInfo(myDefaultValueTypeCodeFragments.get(i), (TypeParameterInfo.New)info, InfoUpdater.DEFAULT_VALUE);
      if (message != null) return message;
      message = updateInfo(myBoundValueTypeCodeFragments.get(i), (TypeParameterInfo.New)info, InfoUpdater.BOUND_VALUE);
      if (message != null) return message;
    }
    return null;
  }

  @NlsContexts.DialogMessage
  private static String updateInfo(PsiTypeCodeFragment source, TypeParameterInfo.New info, InfoUpdater updater) {
    PsiType valueType;
    try {
      valueType = source.getType();
      if (valueType instanceof PsiPrimitiveType) {
        return JavaRefactoringBundle.message("changeClassSignature.Type.parameter.can.not.be.primitive");
      }
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return JavaRefactoringBundle
        .message("changeClassSignature.bad.value", updater.getValueName(), source.getText(), info.getName(null));
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return updater == InfoUpdater.DEFAULT_VALUE
             ? JavaRefactoringBundle.message("changeSignature.no.type.for.parameter", "default value", info.getName(null))
             : null;
    }
    updater.updateInfo(info, valueType);
    return null;
  }

  private class MyTableModel extends AbstractTableModel implements EditableModel {
    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public int getRowCount() {
      return myTypeParameterInfos.size();
    }

    @Override
    @Nullable
    public Class getColumnClass(int columnIndex) {
      return columnIndex == NAME_COLUMN ? String.class : null;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case NAME_COLUMN:
          return myTypeParameterInfos.get(rowIndex).getName(myOriginalParameters);
        case BOUND_VALUE_COLUMN:
          return myBoundValueTypeCodeFragments.get(rowIndex);
        case DEFAULT_VALUE_COLUMN:
          return myDefaultValueTypeCodeFragments.get(rowIndex);
      }
      LOG.assertTrue(false);
      return null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return myTypeParameterInfos.get(rowIndex) instanceof TypeParameterInfo.New;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case NAME_COLUMN:
          return RefactoringBundle.message("column.name.name");
        case BOUND_VALUE_COLUMN:
          return JavaRefactoringBundle.message("changeSignature.bound.value.column");
        case DEFAULT_VALUE_COLUMN:
          return JavaRefactoringBundle.message("changeSignature.default.value.column");
        default:
          LOG.assertTrue(false);
      }
      return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case NAME_COLUMN:
          ((TypeParameterInfo.New)myTypeParameterInfos.get(rowIndex)).setNewName((String)aValue);
          break;
        case BOUND_VALUE_COLUMN:
        case DEFAULT_VALUE_COLUMN:
          break;
        default:
          LOG.assertTrue(false);
      }
    }

    @Override
    public void addRow() {
      TableUtil.stopEditing(myTable);
      myTypeParameterInfos.add(new TypeParameterInfo.New("", null, null));
      JavaCodeFragmentFactory codeFragmentFactory = JavaCodeFragmentFactory.getInstance(myProject);
      PsiElement context = myClass.getLBrace() != null ? myClass.getLBrace() : myClass;
      myBoundValueTypeCodeFragments.add(CommonJavaRefactoringUtil.createTableCodeFragment(null, context, codeFragmentFactory, true));
      myDefaultValueTypeCodeFragments.add(CommonJavaRefactoringUtil.createTableCodeFragment(null, context, codeFragmentFactory, false));
      final int row = myDefaultValueTypeCodeFragments.size() - 1;
      fireTableRowsInserted(row, row);
    }

    @Override
    public void removeRow(int index) {
      myTypeParameterInfos.remove(index);
      myBoundValueTypeCodeFragments.remove(index);
      myDefaultValueTypeCodeFragments.remove(index);
      fireTableDataChanged();
    }

    @Override
    public void exchangeRows(int index1, int index2) {
      ContainerUtil.swapElements(myTypeParameterInfos, index1, index2);
      ContainerUtil.swapElements(myBoundValueTypeCodeFragments, index1, index2);
      ContainerUtil.swapElements(myDefaultValueTypeCodeFragments, index1, index2);
      fireTableDataChanged();
      //fireTableRowsUpdated(Math.min(index1, index2), Math.max(index1, index2));
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }
  }

  private static class MyCellRenderer extends ColoredTableCellRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTable table, Object value,
                                      boolean isSelected, boolean hasFocus, int row, int col) {
      if (value == null) return;
      setPaintFocusBorder(false);
      acquireState(table, isSelected, false, row, col);
      getCellState().updateRenderer(this);
      append((String)value);
    }
  }

  private interface InfoUpdater {
    void updateInfo(TypeParameterInfo.New info, PsiType type);

    String getValueName();

    InfoUpdater DEFAULT_VALUE = new InfoUpdater() {
      @Override
      public void updateInfo(TypeParameterInfo.New info, PsiType type) {
        info.setDefaultValue(type);
      }

      @Override
      public String getValueName() {
        return "default";
      }
    };

    InfoUpdater BOUND_VALUE = new InfoUpdater() {
      @Override
      public void updateInfo(TypeParameterInfo.New info, PsiType type) {
        info.setBoundValue(type);
      }

      @Override
      public String getValueName() {
        return "bound";
      }
    };
  }

  static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("changeClassSignature.refactoring.name");
  }
}
