// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.openapi.ui.cellvalidators.ValidatingTableCellRendererWrapper.CELL_VALIDATION_PROPERTY;

public class StatefulValidatingCellEditor extends DefaultCellEditor implements Supplier<ValidationInfo> {
  private @NotNull Consumer<? super ValidationInfo> stateUpdater = (vi) -> {};

  public StatefulValidatingCellEditor(JTextField textField, Disposable disposable) {
    super(textField);
    editorComponent.putClientProperty("JComponent.compactHeight", Boolean.TRUE);
    new ComponentValidator(disposable).withValidator(this).installOn(editorComponent);

    DocumentListener dl = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        editorComponent.putClientProperty(CELL_VALIDATION_PROPERTY, null);
        ComponentValidator.getInstance(editorComponent).ifPresent(ComponentValidator::revalidate);
      }
    };

    textField.getDocument().addDocumentListener(dl);
    Disposer.register(disposable, () -> textField.getDocument().removeDocumentListener(dl));
  }

  public StatefulValidatingCellEditor(JComboBox comboBox, Disposable disposable) {
    super(comboBox);
    editorComponent.putClientProperty("JComponent.compactHeight", Boolean.TRUE);
    new ComponentValidator(disposable).withValidator(this).installOn(editorComponent);

    ItemListener il = e -> {
      editorComponent.putClientProperty(CELL_VALIDATION_PROPERTY, null);
      ComponentValidator.getInstance(editorComponent).ifPresent(ComponentValidator::revalidate);
    };

    comboBox.addItemListener(il);
    Disposer.register(disposable, () -> comboBox.removeItemListener(il));
  }

  @ApiStatus.Experimental
  public StatefulValidatingCellEditor withStateUpdater(@NotNull Consumer<? super ValidationInfo> stateUpdater) {
    this.stateUpdater = stateUpdater;
    return this;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    JComponent editor = (JComponent)super.getTableCellEditorComponent(table, value, isSelected, row, column);
    JComponent renderer = (JComponent)table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, isSelected, true, row, column);
    ValidationInfo cellInfo = renderer != null ? (ValidationInfo)renderer.getClientProperty(CELL_VALIDATION_PROPERTY) : null;
    if (cellInfo != null) {
      editor.putClientProperty(CELL_VALIDATION_PROPERTY, cellInfo.forComponent(editor));
      ComponentValidator.getInstance(editor).ifPresent(ComponentValidator::revalidate);
    }
    return editor;
  }

  @Override
  public boolean stopCellEditing() {
    editorComponent.putClientProperty(CELL_VALIDATION_PROPERTY, null);
    ComponentValidator.getInstance(editorComponent).ifPresent(ComponentValidator::revalidate);
    return super.stopCellEditing();
  }

  @Override
  public void cancelCellEditing() {
    editorComponent.putClientProperty(CELL_VALIDATION_PROPERTY, null);
    ComponentValidator.getInstance(editorComponent).ifPresent(ComponentValidator::revalidate);
    super.cancelCellEditing();
  }

  @Override
  public ValidationInfo get() {
    ValidationInfo info = (ValidationInfo)editorComponent.getClientProperty(CELL_VALIDATION_PROPERTY);
    stateUpdater.accept(info);
    return info;
  }
}
