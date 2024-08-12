// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.newclass;

import com.intellij.ide.ui.newItemPopup.NewItemWithTemplatesPopupPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.ide.actions.newclass.TemplateListCellRendererKt.createTemplateListCellRenderer;

public class CreateWithTemplatesDialogPanel extends NewItemWithTemplatesPopupPanel<CreateWithTemplatesDialogPanel.TemplatePresentation> {
  public record TemplatePresentation(@Nls @NotNull String kind, @Nullable Icon icon, @NotNull String templateName) {}

  public CreateWithTemplatesDialogPanel(@Nullable String selectedItem, @NotNull List<? extends TemplatePresentation> templates) {
    super(templates, ExperimentalUI.isNewUI() ? createTemplateListCellRenderer() : new TemplateListCellRenderer());
    myTemplatesList.addListSelectionListener(e -> {
      TemplatePresentation selectedValue = myTemplatesList.getSelectedValue();
      if (selectedValue != null) {
        setTextFieldIcon(selectedValue.icon());
      }
    });
    if (ExperimentalUI.isNewUI()) {
      myTemplatesList.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
    }
    selectTemplate(selectedItem);
    setTemplatesListVisible(templates.size() > 1);
  }

  public JTextField getNameField() {
    return myTextField;
  }

  public @NotNull String getEnteredName() {
    return myTextField.getText().trim();
  }

  public @NotNull String getSelectedTemplate() {
    return myTemplatesList.getSelectedValue().templateName();
  }

  private void setTextFieldIcon(Icon icon) {
    myTextField.setExtensions(new TemplateIconExtension(icon));
    myTextField.repaint();
  }

  private void selectTemplate(@Nullable String selectedItem) {
    ListModel<TemplatePresentation> model = myTemplatesList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      String templateID = model.getElementAt(i).templateName();
      if (StringUtil.equals(selectedItem, templateID)) {
        myTemplatesList.setSelectedIndex(i);
        return;
      }
    }

    myTemplatesList.setSelectedIndex(0);
  }

  private static final class TemplateListCellRenderer implements ListCellRenderer<TemplatePresentation> {
    private final ListCellRenderer<TemplatePresentation> delegateRenderer =
      SimpleListCellRenderer.create((@NotNull JBLabel label, TemplatePresentation value, int index) -> {
        if (value != null) {
          label.setText(value.kind());
          label.setIcon(value.icon());
        }
      });

    @Override
    public Component getListCellRendererComponent(JList<? extends TemplatePresentation> list,
                                                  TemplatePresentation value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      JComponent delegate = (JComponent) delegateRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      delegate.setBorder(JBUI.Borders.empty(JBUIScale.scale(3),
                                            JBUIScale.scale(6),
                                            JBUIScale.scale(3),
                                            JBUIScale.scale(1)));
      return delegate;
    }
  }

  private static final class TemplateIconExtension implements ExtendableTextComponent.Extension {
    private final Icon icon;

    private TemplateIconExtension(Icon icon) {this.icon = icon;}

    @Override
    public Icon getIcon(boolean hovered) {
      return icon;
    }

    @Override
    public boolean isIconBeforeText() {
      return true;
    }
  }

}
