package com.intellij.ide.passwordSafe.config;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterPasswordDialog;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The option panel for password safe
 */
public class PasswordSafeOptionsPanel {
  private final PasswordSafeImpl myPasswordSafe;
  /**
   * The password storage policy option
   */
  private JRadioButton myDoNotRememberPasswordsRadioButton;
  /**
   * The password storage policy option
   */
  private JRadioButton myRememberPasswordsUntilClosingRadioButton;
  /**
   * The password storage policy option
   */
  private JRadioButton mySaveOnDiskRadioButton;
  /**
   * The change password button
   */
  private JButton myManagePasswordButton;
  /**
   * The root panel
   */
  private JPanel myRoot;
  private JBLabel myMasterPasswordStateLabel;

  /**
   * The constructor
   *
   * @param passwordSafe the password safe service instance
   */
  public PasswordSafeOptionsPanel(PasswordSafe passwordSafe) {
    myPasswordSafe = (PasswordSafeImpl)passwordSafe;
    myMasterPasswordStateLabel.setForeground(JBColor.BLUE);
    updateMasterPasswordState();
    myManagePasswordButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myPasswordSafe.getMasterKeyProvider().isEmpty()) {
          MasterPasswordDialog.resetMasterPasswordDialog(null, myPasswordSafe.getMasterKeyProvider(), PasswordSafeOptionsPanel.class).show();
        }
        else {
          MasterPasswordDialog.changeMasterPasswordDialog(null, myPasswordSafe.getMasterKeyProvider(), PasswordSafeOptionsPanel.class).show();
        }
        updateMasterPasswordState();
      }
    });
  }

  private void updateMasterPasswordState() {
    boolean empty = myPasswordSafe.getMasterKeyProvider().isMasterPasswordEnabled();
    myMasterPasswordStateLabel.setText(empty ? "Disabled" : "Enabled");
  }

  public void reset(PasswordSafeSettings settings) {
    PasswordSafeSettings.ProviderType t = settings.getProviderType();
    updateMasterPasswordState();
    switch (t) {
      case DO_NOT_STORE:
        myDoNotRememberPasswordsRadioButton.setSelected(true);
        break;
      case MEMORY_ONLY:
        myRememberPasswordsUntilClosingRadioButton.setSelected(true);
        break;
      case MASTER_PASSWORD:
        mySaveOnDiskRadioButton.setSelected(true);
        break;
      default:
        throw new IllegalStateException("Unknown provider type: " + t);
    }
  }

  private PasswordSafeSettings.ProviderType getProviderType() {
    if (myDoNotRememberPasswordsRadioButton.isSelected()) {
      return PasswordSafeSettings.ProviderType.DO_NOT_STORE;
    }
    if (myRememberPasswordsUntilClosingRadioButton.isSelected()) {
      return PasswordSafeSettings.ProviderType.MEMORY_ONLY;
    }
    return PasswordSafeSettings.ProviderType.MASTER_PASSWORD;
  }

  /**
   * Check if the option panel modified the settings
   *
   * @param settings the settings to compare with
   * @return true, if values were modified
   */
  public boolean isModified(PasswordSafeSettings settings) {
    return getProviderType() != settings.getProviderType();
  }

  /**
   * Save UI state to the settings
   *
   * @param settings the settings to use
   */
  public void apply(PasswordSafeSettings settings) {
    settings.setProviderType(getProviderType());
  }

  /**
   * @return the root panel
   */
  public JComponent getRoot() {
    return myRoot;
  }
}
