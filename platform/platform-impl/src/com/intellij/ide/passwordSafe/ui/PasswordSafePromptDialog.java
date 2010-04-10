/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.ui;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The generic password dialog. Use it to ask a password from user with option to remember it.
 */
public class PasswordSafePromptDialog extends DialogWrapper {
  /**
   * The logger instance
   */
  private static final Logger LOG = Logger.getInstance(PasswordSafePromptDialog.class.getName());
  /**
   * The password field
   */
  private JPasswordField myPasswordPasswordField;
  /**
   * The checkbox that allows remembering the password
   */
  private JCheckBox myRememberThePasswordCheckBox;
  /**
   * The root panel
   */
  private JPanel myRoot;
  /**
   * The message label
   */
  private JLabel myMessage;
  /**
   * Prompt label
   */
  private JLabel myPromptLabel;


  /**
   * The private constructor. Note that it does not do init on dialog.
   *
   * @param project      the project
   * @param passwordSafe the passwordSafe instance
   * @param title        the dialog title
   * @param message      the message on the dialog
   */
  private PasswordSafePromptDialog(Project project, PasswordSafeImpl passwordSafe, String title, String message) {
    super(project, true);
    setTitle(title);
    myMessage.setText(message);
    switch (passwordSafe.getSettings().getProviderType()) {
      case MASTER_PASSWORD:
        myRememberThePasswordCheckBox.setEnabled(true);
        myRememberThePasswordCheckBox.setSelected(false);
        myRememberThePasswordCheckBox.setToolTipText("The password will be stored between application sessions.");
        break;
      case MEMORY_ONLY:
        myRememberThePasswordCheckBox.setEnabled(true);
        myRememberThePasswordCheckBox.setSelected(true);
        myRememberThePasswordCheckBox.setToolTipText("The password will be stored only during this application session.");
        break;
      case DO_NOT_STORE:
        myRememberThePasswordCheckBox.setEnabled(false);
        myRememberThePasswordCheckBox.setSelected(false);
        myRememberThePasswordCheckBox.setToolTipText("The password storing is disabled.");
        break;
      default:
        LOG.error("Unknown policy type: " + passwordSafe.getSettings().getProviderType());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myRoot;
  }

  /**
   * Ask password possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link com.intellij.util.ui.UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  public static String askPassword(final Project project,
                                   final String title,
                                   final String message,
                                   final Class<?> requester,
                                   final String key,
                                   boolean resetPassword) {
    return askPassword(project, title, message, requester, key, resetPassword, null);
  }

  /**
   * Ask password possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error to show in the dialog
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  public static String askPassword(final Project project,
                                   final String title,
                                   final String message,
                                   final Class<?> requester,
                                   final String key,
                                   boolean resetPassword, String error) {
    return askPassword(project, title, message, requester, key, resetPassword, error, null, null);
  }

  /**
   * Ask password possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  public static String askPassword(final String title,
                                   final String message,
                                   final Class<?> requester,
                                   final String key,
                                   boolean resetPassword) {
    return askPassword(null, title, message, requester, key, resetPassword);
  }


  /**
   * Ask passphrase possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link com.intellij.util.ui.UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  public static String askPassphrase(final Project project,
                                     final String title,
                                     final String message,
                                     final Class<?> requester,
                                     final String key,
                                     boolean resetPassword) {
    return askPassphrase(project, title, message, requester, key, resetPassword, null);
  }

  /**
   * Ask passphrase possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project (might be null)
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error to show in the dialog
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  public static String askPassphrase(final Project project,
                                     final String title,
                                     final String message,
                                     final Class<?> requester,
                                     final String key,
                                     boolean resetPassword,
                                     String error) {
    return askPassword(project, title, message, requester, key, resetPassword, error, "Passphrase:", "Remember the passphrase");
  }

  /**
   * Ask passphrase possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  public static String askPassphrase(final String title,
                                     final String message,
                                     final Class<?> requester,
                                     final String key,
                                     boolean resetPassword) {
    return askPassphrase(null, title, message, requester, key, resetPassword);
  }


  /**
   * Ask password possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error text to show in the dialog
   * @param promptLabel   the prompt label text
   * @param checkboxLabel the checkbox text   @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  private static String askPassword(final Project project,
                                    final String title,
                                    final String message,
                                    final Class<?> requester,
                                    final String key,
                                    boolean resetPassword, final String error,
                                    final String promptLabel,
                                    final String checkboxLabel) {
    final PasswordSafeImpl ps = (PasswordSafeImpl)PasswordSafe.getInstance();
    try {
      if (resetPassword) {
        ps.removePassword(project, requester, key);
      }
      else {
        String pw = ps.getPassword(project, requester, key);
        if (pw != null) {
          return pw;
        }
      }
    }
    catch (PasswordSafeException ex) {
      // ignore exception on get/reset phase
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to retrieve or reset password", ex);
      }
    }
    final AtomicReference<String> pw = new AtomicReference<String>(null);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        final PasswordSafePromptDialog d = new PasswordSafePromptDialog(project, ps, title, message);
        if (promptLabel != null) {
          d.myPromptLabel.setText(promptLabel);
        }
        if (checkboxLabel != null) {
          d.myRememberThePasswordCheckBox.setText(checkboxLabel);
        }
        d.init();
        d.setErrorText(error);
        d.show();
        if (d.isOK()) {
          String p = new String(d.myPasswordPasswordField.getPassword());
          pw.set(p);
          if (d.myRememberThePasswordCheckBox.isSelected()) {
            try {
              ps.storePassword(project, requester, key, p);
            }
            catch (PasswordSafeException e) {
              Messages.showErrorDialog(project, e.getMessage(), "Failed to store password");
              if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to store password", e);
              }
            }
          }
        }
      }
    });
    return pw.get();
  }
}

