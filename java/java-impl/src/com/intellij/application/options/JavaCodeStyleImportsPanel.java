// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.util.ArrayList;
import java.util.List;

class JavaCodeStyleImportsPanel extends CodeStyleImportsPanelBase {
  private FullyQualifiedNamesInJavadocOptionProvider myFqnInJavadocOption;
  private ListTableModel<InnerClassItem> doNotInsertInnerListModel;

  private static final ColumnInfo<?, ?>[] INNER_CLASS_COLUMNS = {
    new MyColumnInfo(JavaBundle.message("do.not.import.inner.classes.for")) {
      @Override
      public String valueOf(final InnerClassItem innerClass) {
        return innerClass.getName();
      }

      @Override
      public void setValue(final InnerClassItem innerClass, final String name) {
        innerClass.setName(name);
      }
    },
  };
  private TableView<InnerClassItem> mydoNotInsertInnerTable;

  @Override
  protected CodeStyleImportsBaseUI createKotlinUI(JComponent packages, JComponent importLayout) {
    createDoNotImportInnerList();
    myFqnInJavadocOption = new FullyQualifiedNamesInJavadocOptionProvider();

    JavaCodeStyleImportsUI result =
      new JavaCodeStyleImportsUI(packages, importLayout, mydoNotInsertInnerTable, myFqnInJavadocOption.getPanel());
    result.init();
    return result;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    applyLayoutSettings(javaSettings);
    myFqnInJavadocOption.apply(settings);
    javaSettings.setDoNotImportInner(getInnerClassesNames());
    javaSettings.setLayoutOnDemandImportFromSamePackageFirst(myImportLayoutPanel.isLayoutOnDemandImportsFromSamePackageFirst());
  }

  @Override
  public void reset(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    resetLayoutSettings(javaSettings);
    myFqnInJavadocOption.reset(settings);
    for (String name : javaSettings.getDoNotImportInner()) {
      doNotInsertInnerListModel.addRow(new InnerClassItem(name));
    }
    JBCheckBox checkBox = myImportLayoutPanel.getCbLayoutOnDemandImportsFromSamePackageFirst();
    if (checkBox != null) checkBox.setSelected(javaSettings.isLayoutOnDemandImportFromSamePackageFirst());
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    boolean isModified = isModifiedLayoutSettings(javaSettings);
    isModified |= myFqnInJavadocOption.isModified(settings);
    isModified |= !javaSettings.getDoNotImportInner().equals(getInnerClassesNames());
    JBCheckBox checkBox = myImportLayoutPanel.getCbLayoutOnDemandImportsFromSamePackageFirst();
    if (checkBox != null) isModified |= isModified(checkBox, javaSettings.isLayoutOnDemandImportFromSamePackageFirst());
    return isModified;
  }

  private static JavaCodeStyleSettings getJavaSettings(@NotNull CodeStyleSettings settings) {
    return settings.getCustomSettings(JavaCodeStyleSettings.class);
  }

  private void createDoNotImportInnerList() {
    doNotInsertInnerListModel = new ListTableModel<>(INNER_CLASS_COLUMNS);
    mydoNotInsertInnerTable = new TableView<>(doNotInsertInnerListModel);
    mydoNotInsertInnerTable.setShowGrid(false);
    mydoNotInsertInnerTable.getEmptyText().setText(JavaBundle.message("do.not.import.inner.classes.no.classes"));
  }

  private List<String> getInnerClassesNames() {
    final List<String> items = new ArrayList<>();

    for (InnerClassItem item : doNotInsertInnerListModel.getItems()) {
      final String name = item.getName().trim();
      if (!name.isEmpty()) {
        items.add(name);
      }
    }
    return items;
  }

  @Override
  protected boolean isShowLayoutOnDemandImportFromSamePackageFirstCheckbox() {
    return true;
  }

  private abstract static class MyColumnInfo extends ColumnInfo<InnerClassItem, String> {
    MyColumnInfo(final @NlsContexts.ColumnName String name) {
      super(name);
    }

    @Override
    public TableCellEditor getEditor(final InnerClassItem item) {
      final JTextField textField = new JTextField();
      textField.setBorder(BorderFactory.createLineBorder(JBColor.BLACK));
      return new DefaultCellEditor(textField);
    }

    @Override
    public boolean isCellEditable(final InnerClassItem innerClass) {
      return true;
    }
  }

  static class InnerClassItem {
    private String myName;

    InnerClassItem(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    public void setName(String name) {
      myName = name;
    }
  }
}