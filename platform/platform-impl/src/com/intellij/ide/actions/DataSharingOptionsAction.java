/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions;

import com.intellij.ide.gdpr.Consent;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AppUIUtil;

import java.util.Collection;

public class DataSharingOptionsAction extends DumbAwareAction {
  public DataSharingOptionsAction() {
    super("Data Sharing Options...", "Data Sharing Options", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Pair<Collection<Consent>, Boolean> consentsToShow = ConsentOptions.getInstance().getConsents();
    try {
      final Collection<Consent> result = AppUIUtil.confirmConsentOptions(consentsToShow.first);
      if (result != null) {
        ConsentOptions.getInstance().setConsents(result);
      }
    }
    catch (Exception ex) {
      Logger.getInstance(DataSharingOptionsAction.class).warn(ex);
    }
  }
}
