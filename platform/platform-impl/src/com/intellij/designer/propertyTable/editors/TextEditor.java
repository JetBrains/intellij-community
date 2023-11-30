// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable.editors;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Alexander Lobas
 */
public class TextEditor extends PropertyEditor {
  public final JBTextField myTextField = new JBTextField(); // public modifier for accessing from descendants from different jars

  public TextEditor() {
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fireValueCommitted(true, true);
      }
    });
    myTextField.getDocument().addDocumentListener(
      new DocumentAdapter() {
        @Override
        protected void textChanged(final @NotNull DocumentEvent e) {
          preferredSizeChanged();
        }
      }
    );
  }

  @Override
  public @NotNull JComponent getComponent(@Nullable PropertiesContainer container,
                                          @Nullable PropertyContext context, Object value,
                                          @Nullable InplaceContext inplaceContext) {
    setEditorValue(container, value);

    if (inplaceContext == null) {
      myTextField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    }
    else {
      myTextField.setBorder(UIUtil.getTextFieldBorder());
      if (inplaceContext.isStartChar()) {
        myTextField.setText(inplaceContext.getText(myTextField.getText()));
      }
    }
    return myTextField;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextField;
  }

  @Override
  public Object getValue() throws Exception {
    return myTextField.getText();
  }

  protected void setEditorValue(@Nullable PropertiesContainer container, @Nullable Object value) {
    myTextField.setText(value == null ? "" : value.toString());
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}