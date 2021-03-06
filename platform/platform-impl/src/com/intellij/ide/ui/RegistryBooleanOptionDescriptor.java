// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Changeable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryBooleanOptionDescriptor extends BooleanOptionDescription implements Changeable {
  protected final String myKey;

  public RegistryBooleanOptionDescriptor(@NlsContexts.Label String option, String registryKey) {
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
      suggestRestart(parentComponent);
    }
  }

  public static void suggestRestart(@Nullable JComponent parentComponent) {
    ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();

    String title = IdeBundle.message("dialog.title.restart.required");
    String message = IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                       ApplicationNamesInfo.getInstance().getFullProductName());
    String okText = IdeBundle.message("button.now", app.isRestartCapable() ? 0 : 1);
    String cancelText = IdeBundle.message("button.later", app.isRestartCapable() ? 0 : 1);

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
