// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

import static com.intellij.openapi.ui.cellvalidators.ValidatingTableCellRendererWrapper.CELL_VALIDATION_PROPERTY;

public final class ValidationUtils {
  private ValidationUtils() {}

  public final static ExtendableTextComponent.Extension ERROR_EXTENSION =
    ExtendableTextComponent.Extension.create(AllIcons.General.BalloonError, null, null);

  public static void setErrorExtension(ExtendableTextComponent editor, boolean set) {
    if (!set) {
      editor.removeExtension(ERROR_EXTENSION);
    } else if (editor instanceof JComponent && ((JComponent)editor).getClientProperty("JComponent.outline") == null) {
      editor.addExtension(ERROR_EXTENSION);
    }
  }

  public static class StatefulValidatingEditor extends DefaultCellEditor {
    public StatefulValidatingEditor(Disposable disposable) {
      super(new ExtendableTextField());
      editorComponent.putClientProperty("JComponent.compactHeight", Boolean.TRUE);

      ExtendableTextField extendableTextField = (ExtendableTextField)editorComponent;

      new ComponentValidator(disposable).withValidator(() -> {
        ValidationInfo info = (ValidationInfo)extendableTextField.getClientProperty(CELL_VALIDATION_PROPERTY);
        setErrorExtension(extendableTextField, info != null);
        return info;
      }).installOn(extendableTextField);

      DocumentListener dl = new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          editorComponent.putClientProperty(CELL_VALIDATION_PROPERTY, null);
          ComponentValidator.getInstance(editorComponent).ifPresent(ComponentValidator::revalidate);
        }
      };

      extendableTextField.getDocument().addDocumentListener(dl);
      Disposer.register(disposable, () -> extendableTextField.getDocument().removeDocumentListener(dl));
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      JComponent editor = (JComponent)super.getTableCellEditorComponent(table, value, isSelected, row, column);
      JComponent renderer = (JComponent)table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, isSelected, true, row, column);
      ValidationInfo cellInfo = renderer != null ? (ValidationInfo)renderer.getClientProperty(CELL_VALIDATION_PROPERTY) : null;
      if (cellInfo != null) {
        editor.putClientProperty(CELL_VALIDATION_PROPERTY, new ValidationInfo(cellInfo.message, editor));
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
  }
}
