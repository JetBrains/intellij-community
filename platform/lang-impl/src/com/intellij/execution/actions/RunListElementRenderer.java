package com.intellij.execution.actions;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;

import javax.swing.*;
import java.awt.*;

class RunListElementRenderer extends PopupListElementRenderer {
  private JLabel myLabel;
  private final ListPopupImpl myPopup1;
  private final boolean myHasSideBar;

  RunListElementRenderer(ListPopupImpl popup, boolean hasSideBar) {
    super(popup);

    myPopup1 = popup;
    myHasSideBar = hasSideBar;
  }

  @Override
  protected JComponent createItemComponent() {
    if (myLabel == null) {
      myLabel = new JLabel();
      myLabel.setPreferredSize(new JLabel("8.").getPreferredSize());
    }

    final JComponent result = super.createItemComponent();
    result.add(myLabel, BorderLayout.WEST);
    return result;
  }

  @Override
  protected void customizeComponent(JList list, Object value, boolean isSelected) {
    super.customizeComponent(list, value, isSelected);

    myLabel.setVisible(myHasSideBar);

    ListPopupStep<Object> step = myPopup1.getListStep();
    boolean isSelectable = step.isSelectable(value);
    myLabel.setEnabled(isSelectable);
    myLabel.setIcon(null);

    if (isSelected) {
      setSelected(myLabel);
    }
    else {
      setDeselected(myLabel);
    }

    if (value instanceof ChooseRunConfigurationPopup.Wrapper) {
      ChooseRunConfigurationPopup.Wrapper wrapper = (ChooseRunConfigurationPopup.Wrapper)value;
      final int mnemonic = wrapper.getMnemonic();
      if (mnemonic != -1 && !myPopup1.getSpeedSearch().isHoldingFilter()) {
        myLabel.setText(mnemonic + ".");
        myLabel.setDisplayedMnemonicIndex(0);
      }
      else {
        if (wrapper.isChecked()) {
          myTextLabel.setIcon(isSelected ? RunConfigurationsComboBoxAction.CHECKED_SELECTED_ICON
                                         : RunConfigurationsComboBoxAction.CHECKED_ICON);
        }
        else {
          if (myTextLabel.getIcon() == null) {
            myTextLabel.setIcon(RunConfigurationsComboBoxAction.EMPTY_ICON);
          }
        }
        myLabel.setText("");
      }
    }
  }
}
