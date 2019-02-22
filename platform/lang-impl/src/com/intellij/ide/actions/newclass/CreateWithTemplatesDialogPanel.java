// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.newclass;

import com.intellij.openapi.util.Trinity;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;

public class CreateWithTemplatesDialogPanel extends JBPanel {

  private final JTextField myNameField;
  private final JList<Trinity<String, Icon, String>> myTemplatesList;

  public CreateWithTemplatesDialogPanel(@NotNull List<Trinity<String, Icon, String>> templates, @Nullable String selectedItem) {
    super(new BorderLayout());

    myNameField = new JTextField();
    myNameField.setColumns(30);
    myTemplatesList = new JBList<>(templates);

    updateBorder(false);

    ScrollingUtil.installMoveUpAction(myTemplatesList, myNameField);
    ScrollingUtil.installMoveDownAction(myTemplatesList, myNameField);
    myTemplatesList.setCellRenderer(LIST_RENDERER);

    selectTemplate(selectedItem);
    add(myNameField, BorderLayout.NORTH);
    add(myTemplatesList, BorderLayout.CENTER);
  }

  private void updateBorder(boolean error) {
    JBColor borderColor = new JBColor(0xbdbdbd, 0x646464);
    Border border = JBUI.Borders.customLine(borderColor, 1, 0, 1, 0);

    if (error) {
      JBColor errorColor = new JBColor(0xbe0000, 0x750000);
      Border errorBorder = JBUI.Borders.customLine(errorColor, 1);
      border = JBUI.Borders.merge(border, errorBorder, false);
    }

    myNameField.setBorder(border);
  }

  public JTextField getNameField() {
    return myNameField;
  }

  @NotNull
  public String getEnteredName() {
    return myNameField.getText().trim();
  }

  @NotNull
  public String getSelectedTemplate() {
    return myTemplatesList.getSelectedValue().first;
  }

  public void setError(boolean error) {
    updateBorder(error);
  }

  private void selectTemplate(@Nullable String selectedItem) {
    if (selectedItem == null) {
      myTemplatesList.setSelectedIndex(0);
      return;
    }

    ListModel<Trinity<String, Icon, String>> model = myTemplatesList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      String templateID = model.getElementAt(i).getThird();
      if (selectedItem.equals(templateID)) {
        myTemplatesList.setSelectedIndex(i);
        return;
      }
    }
  }

  private static final ListCellRenderer<Trinity<String, Icon, String>> LIST_RENDERER =  new ListCellRendererWrapper<Trinity<String, Icon, String>>() {
    @Override
    public void customize(JList list, Trinity<String, Icon, String> value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        setText(value.first);
        setIcon(value.second);
      }
    }
  };
}
