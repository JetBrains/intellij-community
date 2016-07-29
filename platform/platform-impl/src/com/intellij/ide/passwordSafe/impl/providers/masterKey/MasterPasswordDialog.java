/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.List;

/**
 * The dialog used to prompt for the master password to the password database
 */
public class MasterPasswordDialog extends DialogWrapper {
  private final static int NUMBER_OF_RETRIES = 5;

  private final JPanel myRootPanel = new JPanel(new CardLayout());
  private final List<PasswordComponentBase> myComponents = ContainerUtil.newArrayList();
  private final DialogWrapperAction myCardAction;
  private int myRetriesCount;

  public MasterPasswordDialog(@NotNull PasswordComponentBase component) {
    super(false);

    setResizable(false);
    myComponents.add(component);
    myRootPanel.add(component.getComponent(), component.getTitle());
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
  public void doOKAction() {
    PasswordComponentBase component = getSelectedComponent();
    ValidationInfo info = component.apply();
    if (info == null) {
      super.doOKAction();
    }
    else {
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
