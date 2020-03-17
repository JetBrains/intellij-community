// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Changeable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryBooleanOptionDescriptor extends BooleanOptionDescription implements Changeable {
  protected final String myKey;

  public RegistryBooleanOptionDescriptor(String option, String registryKey) {
    super(option, null);
    myKey = registryKey;
  }

  @Override
  public boolean isOptionEnabled() {
    return Registry.is(myKey);
  }

  @Override
  public void setOptionState(boolean enabled) {
    Registry.get(myKey).setValue(enabled);
    if (!ApplicationManager.getApplication().isUnitTestMode()) suggestRestartIfNecessary(null);
  }

  @Override
  public boolean hasChanged() {
    return Registry.get(myKey).isChangedFromDefault();
  }

  public static void suggestRestartIfNecessary(@Nullable JComponent parentComponent) {
    if (Registry.getInstance().isRestartNeeded()) {
      ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();

      String title = "Restart Required";
      String message = ApplicationNamesInfo.getInstance().getFullProductName() + " must be restarted for the changes to take effect";
      String action = app.isRestartCapable() ? "Restart" : "Shutdown";
      String okText = action + " Now";
      String cancelText = action + " Later";

      int result;
      if (parentComponent != null) {
        result = Messages.showOkCancelDialog(parentComponent, message, title, okText, cancelText, Messages.getQuestionIcon());
      }
      else {
        result = Messages.showOkCancelDialog(message, title, okText, cancelText, Messages.getQuestionIcon());
      }

      if (result == Messages.OK) {
        ApplicationManager.getApplication().invokeLater(() -> app.restart(true), ModalityState.NON_MODAL);
      }
    }
  }
}
