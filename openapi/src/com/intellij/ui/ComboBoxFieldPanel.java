/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;

public class ComboBoxFieldPanel extends AbstractFieldPanel {
  private final JComboBox myComboBox;
  private String oldText;

  public ComboBoxFieldPanel() {
    super(new JComboBox());
    myComboBox = (JComboBox) getComponent();
  }
  public ComboBoxFieldPanel(String[] items, String labelText, final String viewerDialogTitle, ActionListener browseButtonActionListener) {
    this(items, labelText, viewerDialogTitle, browseButtonActionListener, null);
  }

  public ComboBoxFieldPanel(String[] items, String labelText, final String viewerDialogTitle, ActionListener browseButtonActionListener, final Runnable documentListener) {
    super(new JComboBox(items), labelText, viewerDialogTitle, browseButtonActionListener, documentListener);

    myComboBox = (JComboBox) getComponent();
    createComponent();
  }

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
      protected void textChanged(DocumentEvent e) {
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

  public String getText() {
    final Object selectedItem = myComboBox.getEditor().getItem();
    return selectedItem instanceof String ? (String)selectedItem : null;
  }

  public void setText(String text) {
    myComboBox.setSelectedItem(text);
  }

  public JComboBox getComboBox() {
    return myComboBox;
  }

  public void setItems(Object[] items) {
    myComboBox.removeAllItems();
    for (int i = 0; i < items.length; i++) {
      Object item = items[i];
      myComboBox.addItem(item);
    }
  }

  public void addItemSetText(String text) {
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
