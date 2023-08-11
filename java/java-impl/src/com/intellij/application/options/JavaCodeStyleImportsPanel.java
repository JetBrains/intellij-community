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
package com.intellij.application.options;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.ui.JBColor;
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
  }

  @Override
  public void reset(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    resetLayoutSettings(javaSettings);
    myFqnInJavadocOption.reset(settings);
    for (String name : javaSettings.getDoNotImportInner()) {
      doNotInsertInnerListModel.addRow(new InnerClassItem(name));
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    boolean isModified = isModifiedLayoutSettings(javaSettings);
    isModified |= myFqnInJavadocOption.isModified(settings);
    isModified |= !javaSettings.getDoNotImportInner().equals(getInnerClassesNames());
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

  private static abstract class MyColumnInfo extends ColumnInfo<InnerClassItem, String> {
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