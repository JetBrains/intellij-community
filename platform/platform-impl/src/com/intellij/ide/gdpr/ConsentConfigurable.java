// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class ConsentConfigurable extends ConfigurableBase<ConsentSettingsUi, List<Consent>> {
  private final List<Consent> myConsents;

  public ConsentConfigurable() {
    super("consents", IdeBundle.message("consent.configurable"), "preferences.usage.statistics");
    myConsents = new ArrayList<>(AppUIUtil.loadConsentsForEditing());
  }

  @Override
  protected @NotNull List<Consent> getSettings() {
    return myConsents;
  }

  @Override
  protected ConsentSettingsUi createUi() {
    ConsentSettingsUi ui = new ConsentSettingsUi(true) {
      @Override
      public void apply(@NotNull List<Consent> consents) {
        super.apply(consents);
        AppUIUtil.saveConsents(consents);
      }
    };

    // When building searchable options, ensure we return a non-empty UI
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      ui.reset(myConsents);
    }
    return ui;
  }
}
