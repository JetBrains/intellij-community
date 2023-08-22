// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;

public class ComboBoxFieldPanel extends AbstractFieldPanel {
  private final JComboBox myComboBox;
  private String oldText;

  public ComboBoxFieldPanel() {
    super(new ComboBox());
    myComboBox = (JComboBox) getComponent();
  }
  public ComboBoxFieldPanel(String[] items, @NlsContexts.Label String labelText, final @NlsContexts.DialogTitle String viewerDialogTitle, ActionListener browseButtonActionListener) {
    this(items, labelText, viewerDialogTitle, browseButtonActionListener, null);
  }

  public ComboBoxFieldPanel(String[] items, @NlsContexts.Label String labelText, final @NlsContexts.DialogTitle String viewerDialogTitle, ActionListener browseButtonActionListener, final Runnable documentListener) {
    super(new ComboBox(items), labelText, viewerDialogTitle, browseButtonActionListener, documentListener);

    myComboBox = (JComboBox) getComponent();
    createComponent();
  }

  @Override
  public void createComponent() {
    super.createComponent();
    TextFieldWithBrowseButton.MyDoClickAction doClickAction = getDoClickAction();
    if (doClickAction != null) {
      doClickAction.registerShortcut(myComboBox);
    }

    myComboBox.setMaximumRowCount(8);

    myComboBox.setEditable(true);
    final JTextField editorComponent = (JTextField)myComboBox.getEditor().getEditorComponent();
    editorComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        final String text = getText();
        if (!Comparing.equal(text, oldText, true)) {
          oldText = text;
          final Runnable changeListener = getChangeListener();
          if (changeListener != null) {
            changeListener.run();
          }
        }
      }
    });
  }

  @Override
  public String getText() {
    final Object selectedItem = myComboBox.isEditable()? myComboBox.getEditor().getItem() : myComboBox.getSelectedItem();
    return selectedItem instanceof String ? (String)selectedItem : null;
  }

  @Override
  public void setText(String text) {
    myComboBox.setSelectedItem(text);
  }

  public JComboBox getComboBox() {
    return myComboBox;
  }

  public void setItems(Object[] items) {
    myComboBox.removeAllItems();
    for (Object item : items) {
      myComboBox.addItem(item);
    }
  }

  public void addItemSetText(@NlsContexts.ListItem String text) {
    JComboBox comboBox = getComboBox();
    int n = comboBox.getItemCount();
    boolean found = false;
    for (int i = 0; i < n; i++) {
      String item = (String)comboBox.getItemAt(i);
      if (Comparing.strEqual(item, text)) {
        found = true;
        break;
      }
    }
    if (!found) {
      comboBox.addItem(text);
    }
    comboBox.getEditor().setItem(text);
    setText(text);
  }
}
