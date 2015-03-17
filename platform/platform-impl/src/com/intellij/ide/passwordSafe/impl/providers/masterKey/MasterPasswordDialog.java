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
package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;

/**
 * The dialog used to prompt for the master password to the password database
 */
public class MasterPasswordDialog extends DialogWrapper {
  private final static int NUMBER_OF_RETRIES = 5;

  /**
   * @noinspection FieldCanBeLocal
   */
  private final Class<?> myRequestor;
  private final JPanel myRootPanel = new JPanel(new CardLayout());
  private final List<PasswordComponentBase> myComponents = ContainerUtil.newArrayList();
  private final DialogWrapperAction myCardAction;
  private int myRetriesCount;

  /**
   * Ask password from user and set it to password safe instance
   *
   * @param project   the current project
   * @param safe      the password safe
   * @param requestor
   * @throws PasswordSafeException if the master password is not provided.
   */
  public static void askPassword(@Nullable Project project, @NotNull MasterKeyPasswordSafe safe, @NotNull Class<?> requestor)
    throws PasswordSafeException {
    // trying empty password: people who have set up empty password, don't want to get disturbed by the prompt.
    if (safe.setMasterPassword("")) {
      return;
    }

    if (!enterMasterPasswordDialog(project, safe, requestor).showAndGet()) {
      throw new MasterPasswordUnavailableException(PasswordComponentBase.getRequestorTitle(requestor) + ": Cancelled by user");
    }
  }

  public static MasterPasswordDialog resetMasterPasswordDialog(@Nullable Project project,
                                                               @NotNull MasterKeyPasswordSafe safe,
                                                               @NotNull Class<?> requestor) {
    return new MasterPasswordDialog(project, requestor, new ResetPasswordComponent(safe, true));
  }

  public static MasterPasswordDialog changeMasterPasswordDialog(@Nullable Project project,
                                                                @NotNull MasterKeyPasswordSafe safe,
                                                                Class<?> requestor) {
    return new MasterPasswordDialog(project, requestor, new ChangePasswordComponent(safe), new ResetPasswordComponent(safe, false));
  }

  public static MasterPasswordDialog enterMasterPasswordDialog(@Nullable Project project,
                                                               @NotNull MasterKeyPasswordSafe safe,
                                                               @NotNull Class<?> requestor) {
    return new MasterPasswordDialog(project, requestor, new EnterPasswordComponent(safe, requestor), new ResetPasswordComponent(safe, false));
  }

  protected MasterPasswordDialog(@Nullable Project project, Class<?> requestor, PasswordComponentBase... components) {
    super(project, false);
    myRequestor = requestor;
    setResizable(false);
    assert components.length > 0;
    myComponents.addAll(Arrays.asList(components));
    for (PasswordComponentBase component : myComponents) {
      myRootPanel.add(component.getComponent(), component.getTitle());
    }
    myCardAction = new DialogWrapperAction("") {
      @Override
      protected void doAction(ActionEvent e) {
        show(getNextComponent(getSelectedComponent()));
      }
    };
    show(myComponents.get(0));
    init();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getSelectedComponent().getPreferredFocusedComponent();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return getSelectedComponent().getHelpId();
  }

  private CardLayout getLayout() {
    return (CardLayout)myRootPanel.getLayout();
  }

  private PasswordComponentBase getSelectedComponent() {
    for (PasswordComponentBase component : myComponents) {
      if (component.getComponent().isVisible()) return component;
    }
    throw new AssertionError("no visible components");
  }

  @NotNull
  private PasswordComponentBase getNextComponent(@NotNull PasswordComponentBase component) {
    int idx = myComponents.indexOf(component);
    int next = idx < myComponents.size() - 1 ? idx + 1 : 0;
    return myComponents.get(next);
  }

  private void show(@NotNull PasswordComponentBase component) {
    setTitle(component.getTitle() + " Master Password");
    getLayout().show(myRootPanel, component.getTitle());
    myCardAction.putValue(Action.NAME, getNextComponent(component).getTitle() + "...");
    component.getPreferredFocusedComponent().requestFocus();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    Action[] result = {
      getHelpAction(),
      getOKAction(),
      getCancelAction()};
    if (myComponents.size() > 1) {
      return ArrayUtil.append(result, myCardAction);
    }
    return result;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    return getSelectedComponent().doValidate();
  }

  @Override
  protected void doOKAction() {
    PasswordComponentBase component = getSelectedComponent();
    if (component.apply()) {
      super.doOKAction();
    }
    else {
      ValidationInfo info = component.validatePassword();
      if (info != null) {
        setErrorText(info.message + " " + StringUtil.repeat(".", myRetriesCount));
        if (info.component != null) {
          info.component.requestFocus();
        }
        if (++myRetriesCount > NUMBER_OF_RETRIES) {
          super.doCancelAction();
        }
      }
    }
  }
}
