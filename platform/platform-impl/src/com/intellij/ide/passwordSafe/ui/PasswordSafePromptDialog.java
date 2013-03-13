/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The generic password dialog. Use it to ask a password from user with option to remember it.
 */
public class PasswordSafePromptDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PasswordSafePromptDialog.class.getName());

  private JPasswordField myPasswordField;
  private JCheckBox myRememberPasswordCheckBox;
  private JPanel myRootPanel;
  private JLabel myMessageLabel;
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
    myMessageLabel.setText(message);
    switch (passwordSafe.getSettings().getProviderType()) {
      case MASTER_PASSWORD:
        myRememberPasswordCheckBox.setEnabled(true);
        myRememberPasswordCheckBox.setSelected(true);
        myRememberPasswordCheckBox.setToolTipText("The password will be stored between application sessions.");
        break;
      case MEMORY_ONLY:
        myRememberPasswordCheckBox.setEnabled(true);
        myRememberPasswordCheckBox.setSelected(true);
        myRememberPasswordCheckBox.setToolTipText("The password will be stored only during this application session.");
        break;
      case DO_NOT_STORE:
        myRememberPasswordCheckBox.setEnabled(false);
        myRememberPasswordCheckBox.setSelected(false);
        myRememberPasswordCheckBox.setToolTipText("The password storing is disabled.");
        break;
      default:
        LOG.error("Unknown policy type: " + passwordSafe.getSettings().getProviderType());
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }

  /**
   * Ask password possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project
   * @param modalityState the modality state using which any prompts initiated by the git process should be shown in the UI.
   *                      If null then {@link ModalityState#defaultModalityState() the default modality state} will be used.
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error to show in the dialog       @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  @Nullable
  public static String askPassword(final Project project,
                                   @Nullable ModalityState modalityState, final String title,
                                   final String message,
                                   final Class<?> requester,
                                   final String key,
                                   boolean resetPassword, String error) {
    return askPassword(project, modalityState, title, message, requester, key, resetPassword, error, null, null);
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
  @Nullable
  public static String askPassword(final String title,
                                   final String message,
                                   final Class<?> requester,
                                   final String key,
                                   boolean resetPassword) {
    return askPassword(null, null, title, message, requester, key, resetPassword, null);
  }


  /**
   * Ask passphrase possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project (might be null)
   * @param modalityState the modality state using which any prompts initiated by the git process should be shown in the UI.
   *                      If null then {@link ModalityState#defaultModalityState() the default modality state} will be used.
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error to show in the dialog       @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  @Nullable
  public static String askPassphrase(final Project project,
                                     @Nullable ModalityState modalityState, final String title,
                                     final String message,
                                     final Class<?> requester,
                                     final String key,
                                     boolean resetPassword,
                                     String error) {
    return askPassword(project, modalityState, title, message, requester, key, resetPassword, error,
                       "Passphrase:", "Remember the passphrase");
  }


  /**
   * Ask password possibly asking password database first. The method could be invoked from any thread. If UI needs to be shown,
   * the method invokes {@link UIUtil#invokeAndWaitIfNeeded(Runnable)}
   *
   * @param project       the context project
   * @param modalityState the modality state using which any prompts initiated by the git process should be shown in the UI.
   *                      If null then {@link ModalityState#defaultModalityState() the default modality state} will be used.
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requester     the password requester
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error text to show in the dialog
   * @param promptLabel   the prompt label text
   * @param checkboxLabel the checkbox text   @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  @Nullable
  private static String askPassword(final Project project,
                                    @Nullable ModalityState modalityState, final String title,
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
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        final PasswordSafePromptDialog d = new PasswordSafePromptDialog(project, ps, title, message);
        if (promptLabel != null) {
          d.myPromptLabel.setText(promptLabel);
        }
        if (checkboxLabel != null) {
          d.myRememberPasswordCheckBox.setText(checkboxLabel);
        }
        d.init();
        d.setErrorText(error);
        d.show();
        if (d.isOK()) {
          String p = new String(d.myPasswordField.getPassword());
          pw.set(p);
          try {
            if (d.myRememberPasswordCheckBox.isSelected()) {
              ps.storePassword(project, requester, key, p);
            }
            else if (!ps.getSettings().getProviderType().equals(PasswordSafeSettings.ProviderType.DO_NOT_STORE)) {
              ps.getMemoryProvider().storePassword(project, requester, key, p);
            }
          }
          catch (PasswordSafeException e) {
            Messages.showErrorDialog(project, e.getMessage(), "Failed to store password");
            if (LOG.isDebugEnabled()) {
              LOG.debug("Failed to store password", e);
            }
          }
        }
      }
    }, modalityState == null ? ModalityState.defaultModalityState() : modalityState);
    return pw.get();
  }
}

