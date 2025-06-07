package com.intellij.database.run.ui;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.database.datagrid.GridPagingModel.UNLIMITED_PAGE_SIZE;

/**
 * @author Liudmila Kornilova
 **/
public class CustomPageSizeForm {
  private JBCheckBox myLimitCheckBox;
  private JPanel myPanel;
  private IntegerField myResultPageSizeTextField;

  public CustomPageSizeForm() {
    myResultPageSizeTextField.setEnabled(false);
    myLimitCheckBox.addChangeListener(e -> myResultPageSizeTextField.setEnabled(myLimitCheckBox.isSelected()));
  }

  public IntegerField getResultPageSizeTextField() {
    return myResultPageSizeTextField;
  }

  public void reset(boolean limitPageSize, int pageSize) {
    myLimitCheckBox.setSelected(limitPageSize);
    myResultPageSizeTextField.setValue(pageSize);
  }

  public @NotNull JPanel getPanel() {
    return myPanel;
  }

  public int getPageSize() {
    if (!myLimitCheckBox.isSelected()) return UNLIMITED_PAGE_SIZE;
    return myResultPageSizeTextField.getValue();
  }
}
