package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public abstract class CheckboxAction extends ToggleAction implements CustomComponentAction {
  private JCheckBox myCheckBox;

  protected CheckboxAction() {}

  protected CheckboxAction(final String text) {
    super(text);
  }

  protected CheckboxAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  public JComponent createCustomComponent(Presentation presentation) {
    myCheckBox = new JCheckBox(presentation.getText());
    myCheckBox.setToolTipText(presentation.getDescription());
    myCheckBox.setMnemonic(presentation.getMnemonic());
    myCheckBox.setDisplayedMnemonicIndex(presentation.getDisplayedMnemonicIndex());

    myCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        CheckboxAction.this.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(myCheckBox),
                                                              ActionPlaces.UNKNOWN, CheckboxAction.this.getTemplatePresentation(),
                                                              ActionManager.getInstance(), 0));
      }
    });

    return myCheckBox;
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    if (myCheckBox != null) {
      myCheckBox.setSelected(((Boolean)e.getPresentation().getClientProperty(SELECTED_PROPERTY)).booleanValue());
      /* TODO: react on property change
      myCheckBox.setEnabled(e.getPresentation().isEnabled());
      myCheckBox.setVisible(e.getPresentation().isVisible());
      */
    }
  }
}
