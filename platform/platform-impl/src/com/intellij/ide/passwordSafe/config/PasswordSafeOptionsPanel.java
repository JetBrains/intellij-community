package com.intellij.ide.passwordSafe.config;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterPasswordDialog;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The option panel for password safe
 */
public class PasswordSafeOptionsPanel {
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
  private JRadioButton myRememberOnDiskProtectedRadioButton;
  /**
   * The change password button
   */
  private JButton myManagePasswordButton;
  /**
   * The root panel
   */
  private JPanel myRoot;

  /**
   * The constructor
   *
   * @param passwordSafe the password safe service instance
   */
  public PasswordSafeOptionsPanel(PasswordSafe passwordSafe) {
    final PasswordSafeImpl ps = (PasswordSafeImpl)passwordSafe;
    final ChangeListener listener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        boolean isDisk = myRememberOnDiskProtectedRadioButton.isSelected();
        myManagePasswordButton.setEnabled(isDisk);
      }
    };
    myRememberOnDiskProtectedRadioButton.addChangeListener(listener);
    listener.stateChanged(null);
    myManagePasswordButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (isMasterKeyEmpty(ps)) {
          MasterPasswordDialog.resetMasterPasswordDialog(null, ps.getMasterKeyProvider(), PasswordSafeOptionsPanel.class).show();
        }
        else {
          MasterPasswordDialog.changeMasterPasswordDialog(null, ps.getMasterKeyProvider(), PasswordSafeOptionsPanel.class).show();
        }
        listener.stateChanged(null);
      }
    });
  }

  /**
   * Check if master key provider is empty
   *
   * @param ps the password safe component
   * @return true if the provider is empty
   */
  private static boolean isMasterKeyEmpty(PasswordSafeImpl ps) {
    return ps.getMasterKeyProvider().isEmpty();
  }

  /**
   * Load component state from settings
   *
   * @param settings the settings to use
   */
  public void load(PasswordSafeSettings settings) {
    PasswordSafeSettings.ProviderType t = settings.getProviderType();
    switch (t) {
      case DO_NOT_STORE:
        myDoNotRememberPasswordsRadioButton.setSelected(true);
        break;
      case MEMORY_ONLY:
        myRememberPasswordsUntilClosingRadioButton.setSelected(true);
        break;
      case MASTER_PASSWORD:
        myRememberOnDiskProtectedRadioButton.setSelected(true);
        break;
      default:
        throw new IllegalStateException("Unknown provider type: " + t);
    }
  }

  /**
   * @return the provider type
   */
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
  public void save(PasswordSafeSettings settings) {
    settings.setProviderType(getProviderType());
  }

  /**
   * @return the root panel
   */
  public JComponent getRoot() {
    return myRoot;
  }
}
